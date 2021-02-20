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
package de.tudarmstadt.ukp.clarin.webanno.api.annotation.event;

import org.apache.uima.cas.text.AnnotationFS;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;

public class ChainLinkDeletedEvent
    extends ChainLinkEvent
    implements AnnotationDeletedEvent
{
    private static final long serialVersionUID = -1577299485779966337L;

    public ChainLinkDeletedEvent(Object aSource, SourceDocument aDocument, String aUser,
            AnnotationLayer aLayer, AnnotationFS aAnnotation, AnnotationFS aNextLink)
    {
        super(aSource, aDocument, aUser, aLayer, aAnnotation, aNextLink);
    }
}
