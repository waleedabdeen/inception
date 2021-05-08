/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering;

import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.COREFERENCE_RELATION_FEATURE;
import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.COREFERENCE_TYPE_FEATURE;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.TypeUtil.getUiLabelText;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.apache.uima.fit.util.CasUtil.selectFS;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.ChainAdapter;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.adapter.SpanLayerBehavior;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.feature.FeatureSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.layer.LayerSupportRegistry;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.VID;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VArc;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VDocument;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VObject;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VRange;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.model.VSpan;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.TypeUtil;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;

public class ChainRenderer
    extends Renderer_ImplBase<ChainAdapter>
{
    private final List<SpanLayerBehavior> behaviors;

    private Type chainType;
    private Feature chainFirst;

    public ChainRenderer(ChainAdapter aTypeAdapter, LayerSupportRegistry aLayerSupportRegistry,
            FeatureSupportRegistry aFeatureSupportRegistry, List<SpanLayerBehavior> aBehaviors)
    {
        super(aTypeAdapter, aLayerSupportRegistry, aFeatureSupportRegistry);

        if (aBehaviors == null) {
            behaviors = emptyList();
        }
        else {
            List<SpanLayerBehavior> temp = new ArrayList<>(aBehaviors);
            AnnotationAwareOrderComparator.sort(temp);
            behaviors = temp;
        }
    }

    @Override
    protected boolean typeSystemInit(TypeSystem aTypeSystem)
    {
        ChainAdapter typeAdapter = getTypeAdapter();
        try {
            chainType = aTypeSystem.getType(typeAdapter.getChainTypeName());
        }
        catch (IllegalArgumentException e) {
            return false;
        }

        chainFirst = chainType.getFeatureByBaseName(typeAdapter.getChainFirstFeatureName());

        return true;
    }

    @Override
    public void render(CAS aCas, List<AnnotationFeature> aFeatures, VDocument aResponse,
            int aPageBegin, int aPageEnd)
    {
        if (!checkTypeSystem(aCas)) {
            return;
        }

        ChainAdapter typeAdapter = getTypeAdapter();

        // Find the features for the arc and span labels - it is possible that we do not find a
        // feature for arc/span labels because they may have been disabled.
        AnnotationFeature spanLabelFeature = null;
        AnnotationFeature arcLabelFeature = null;
        for (AnnotationFeature f : aFeatures) {
            if (COREFERENCE_TYPE_FEATURE.equals(f.getName())) {
                spanLabelFeature = f;
            }
            if (COREFERENCE_RELATION_FEATURE.equals(f.getName())) {
                arcLabelFeature = f;
            }
        }
        // At this point arc and span feature labels must have been found! If not, the later code
        // will crash.

        // Sorted index mapping annotations to the corresponding rendered spans
        Map<AnnotationFS, VSpan> annoToSpanIdx = new HashMap<>();

        int colorIndex = 0;
        // Iterate over the chains
        for (FeatureStructure chainFs : selectFS(aCas, chainType)) {
            AnnotationFS linkFs = (AnnotationFS) chainFs.getFeatureValue(chainFirst);
            AnnotationFS prevLinkFs = null;

            // Iterate over the links of the chain
            while (linkFs != null) {
                Feature linkNext = linkFs.getType()
                        .getFeatureByBaseName(typeAdapter.getLinkNextFeatureName());
                AnnotationFS nextLinkFs = (AnnotationFS) linkFs.getFeatureValue(linkNext);

                // Is link after window? If yes, we can skip the rest of the chain
                if (linkFs.getBegin() >= aPageEnd) {
                    break; // Go to next chain
                }

                // Is link before window? We only need links that being within the window and that
                // end within the window
                if (!(linkFs.getBegin() >= aPageBegin) && (linkFs.getEnd() <= aPageEnd)) {
                    // prevLinkFs remains null until we enter the window
                    linkFs = nextLinkFs;
                    continue; // Go to next link
                }

                String bratTypeName = typeAdapter.getEncodedTypeName();

                // Render span
                {
                    String bratLabelText = TypeUtil.getUiLabelText(typeAdapter, linkFs,
                            (spanLabelFeature != null) ? asList(spanLabelFeature) : emptyList());
                    VRange offsets = new VRange(linkFs.getBegin() - aPageBegin,
                            linkFs.getEnd() - aPageBegin);

                    VSpan span = new VSpan(typeAdapter.getLayer(), linkFs, bratTypeName, offsets,
                            colorIndex, bratLabelText);
                    annoToSpanIdx.put(linkFs, span);
                    aResponse.add(span);

                    renderLazyDetails(linkFs, span, aFeatures);
                }

                // Render arc (we do this on prevLinkFs because then we easily know that the current
                // and last link are within the window ;)
                if (prevLinkFs != null) {
                    String bratLabelText = null;

                    if (typeAdapter.isLinkedListBehavior() && arcLabelFeature != null) {
                        // Render arc label
                        bratLabelText = getUiLabelText(typeAdapter, prevLinkFs,
                                asList(arcLabelFeature));
                    }
                    else {
                        // Render only chain type
                        bratLabelText = getUiLabelText(typeAdapter, prevLinkFs, emptyList());
                    }

                    aResponse.add(new VArc(typeAdapter.getLayer(),
                            new VID(prevLinkFs, 1, VID.NONE, VID.NONE), bratTypeName, prevLinkFs,
                            linkFs, colorIndex, bratLabelText));
                }

                // Render errors if required features are missing
                renderRequiredFeatureErrors(aFeatures, linkFs, aResponse);

                prevLinkFs = linkFs;
                linkFs = nextLinkFs;
            }

            // The color index is updated even for chains that have no visible links in the current
            // window because we would like the chain color to be independent of visibility. In
            // particular the color of a chain should not change when switching pages/scrolling.
            colorIndex++;
        }

        for (SpanLayerBehavior behavior : behaviors) {
            behavior.onRender(typeAdapter, aResponse, annoToSpanIdx, aPageBegin, aPageEnd);
        }
    }

    @Override
    public List<VObject> render(AnnotationFS aFS, List<AnnotationFeature> aFeatures,
            int aWindowBegin)
    {
        return Collections.emptyList();
    }
}