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
package de.tudarmstadt.ukp.clarin.webanno.ui.curation.page;

import static de.tudarmstadt.ukp.clarin.webanno.api.CasUpgradeMode.AUTO_CAS_UPGRADE;
import static de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst.CURATION_USER;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorStateUtils.updateDocumentTimestampAfterWrite;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorStateUtils.verifyAndUpdateDocumentTimestamp;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.page.AnnotationPageBase.PAGE_PARAM_DOCUMENT;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.paging.FocusPosition.CENTERED;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.paging.FocusPosition.TOP;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getAddr;
import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.getFirstSentence;
import static de.tudarmstadt.ukp.clarin.webanno.api.casstorage.CasAccessMode.SHARED_READ_ONLY_ACCESS;
import static de.tudarmstadt.ukp.clarin.webanno.api.casstorage.CasAccessMode.UNMANAGED_ACCESS;
import static de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.doDiffSingle;
import static de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.getDiffAdapters;
import static de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.LinkCompareBehavior.LINK_ROLE_AS_LABEL;
import static de.tudarmstadt.ukp.clarin.webanno.model.PermissionLevel.CURATOR;
import static de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentState.CURATION_FINISHED;
import static de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentStateTransition.ANNOTATION_IN_PROGRESS_TO_CURATION_IN_PROGRESS;
import static de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior.enabledWhen;
import static de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior.visibleWhen;
import static de.tudarmstadt.ukp.clarin.webanno.ui.core.page.ProjectPageBase.NS_PROJECT;
import static de.tudarmstadt.ukp.clarin.webanno.ui.core.page.ProjectPageBase.PAGE_PARAM_PROJECT;
import static de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.model.SentenceState.AGREE;
import static de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.model.SentenceState.DISAGREE;
import static org.apache.uima.fit.util.CasUtil.getType;
import static org.apache.uima.fit.util.CasUtil.selectCovered;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.feedback.IFeedback;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.head.OnLoadHeaderItem;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.ListModel;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.apache.wicket.util.string.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.wicketstuff.annotation.mount.MountPath;
import org.wicketstuff.event.annotation.OnEvent;

import de.tudarmstadt.ukp.clarin.webanno.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.clarin.webanno.api.DocumentService;
import de.tudarmstadt.ukp.clarin.webanno.api.ProjectService;
import de.tudarmstadt.ukp.clarin.webanno.api.WebAnnoConst;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.AnnotationEditorBase;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.actionbar.ActionBar;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.event.AnnotationEvent;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.exception.AnnotationException;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorState;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.model.AnnotatorStateImpl;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.page.AnnotationPageBase;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.paging.SentenceOrientedPagingStrategy;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.event.RenderAnnotationsEvent;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.event.SelectionChangedEvent;
import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.clarin.webanno.brat.annotation.BratAnnotationEditor;
import de.tudarmstadt.ukp.clarin.webanno.constraints.ConstraintsService;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.Configuration;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.ConfigurationSet;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.CasDiff.DiffResult;
import de.tudarmstadt.ukp.clarin.webanno.curation.casdiff.api.DiffAdapter;
import de.tudarmstadt.ukp.clarin.webanno.curation.casmerge.CasMerge;
import de.tudarmstadt.ukp.clarin.webanno.curation.storage.CurationDocumentService;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.Mode;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.security.model.Role;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.clarin.webanno.support.StopWatch;
import de.tudarmstadt.ukp.clarin.webanno.support.lambda.LambdaBehavior;
import de.tudarmstadt.ukp.clarin.webanno.support.wicket.DecoratedObject;
import de.tudarmstadt.ukp.clarin.webanno.support.wicket.WicketUtil;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.component.DocumentNamePanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.detail.AnnotationDetailEditorPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.AnnotatorsPanel;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.model.AnnotationSelection;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.model.AnnotatorSegment;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.model.SentenceIndex;
import de.tudarmstadt.ukp.clarin.webanno.ui.curation.component.model.SentenceInfo;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;

/**
 * This is the main class for the curation page. It contains an interface which displays differences
 * between user annotations for a specific document. The interface provides a tool for merging these
 * annotations and storing them as a new annotation.
 */
@MountPath(NS_PROJECT + "/${" + PAGE_PARAM_PROJECT + "}/curate/#{" + PAGE_PARAM_DOCUMENT + "}")
public class CurationPage
    extends AnnotationPageBase
{
    private static final String MID_NUMBER_OF_PAGES = "numberOfPages";

    private final static Logger LOG = LoggerFactory.getLogger(CurationPage.class);

    private static final long serialVersionUID = 1378872465851908515L;

    private @SpringBean DocumentService documentService;
    private @SpringBean CurationDocumentService curationDocumentService;
    private @SpringBean ProjectService projectService;
    private @SpringBean ConstraintsService constraintsService;
    private @SpringBean AnnotationSchemaService annotationService;
    private @SpringBean UserDao userRepository;

    private long currentprojectId;

    // Open the dialog window on first load
    private boolean firstLoad = true;

    private SentenceIndex curationContainer;

    private WebMarkupContainer centerArea;
    private WebMarkupContainer actionBar;

    private AnnotatorsPanel annotatorsPanel;

    private WebMarkupContainer leftSidebar;
    private WebMarkupContainer sentenceList;

    private AnnotationEditorBase annotationEditor;
    private AnnotationDetailEditorPanel detailPanel;

    private SentenceInfo curationView;
    private List<SentenceInfo> sourceListModel;

    private int firstVisibleUnitIndex = 0;
    private int lastVisibleUnitIndex = 0;

    /**
     * Map for tracking curated spans. Key contains the address of the span, the value contains the
     * username from which the span has been selected
     */
    private Map<String, Map<Integer, AnnotationSelection>> annotationSelectionByUsernameAndAddress = new HashMap<>();

    public CurationPage(final PageParameters aPageParameters)
    {
        super(aPageParameters);

        LOG.debug("Setting up curation page with parameters: {}", aPageParameters);

        AnnotatorState state = new AnnotatorStateImpl(Mode.CURATION);
        setModel(Model.of(state));

        User user = userRepository.getCurrentUser();

        requireProjectRole(user, CURATOR);

        StringValue document = aPageParameters.get(PAGE_PARAM_DOCUMENT);
        StringValue focus = aPageParameters.get(PAGE_PARAM_FOCUS);

        handleParameters(document, focus, true);

        commonInit();

        updateDocumentView(null, null, focus);
    }

    private void commonInit()
    {
        add(createUrlFragmentBehavior());

        centerArea = new WebMarkupContainer("centerArea");
        centerArea.add(visibleWhen(() -> getModelObject().getDocument() != null));
        centerArea.setOutputMarkupPlaceholderTag(true);
        centerArea.add(new DocumentNamePanel("documentNamePanel", getModel()));
        add(centerArea);

        actionBar = new ActionBar("actionBar");
        centerArea.add(actionBar);

        getModelObject().setPagingStrategy(new SentenceOrientedPagingStrategy());
        centerArea.add(getModelObject().getPagingStrategy()
                .createPositionLabel(MID_NUMBER_OF_PAGES, getModel())
                .add(visibleWhen(() -> getModelObject().getDocument() != null))
                .add(LambdaBehavior.onEvent(RenderAnnotationsEvent.class,
                        (c, e) -> e.getRequestHandler().add(c))));

        // Ensure that a user is set
        getModelObject().setUser(new User(CURATION_USER, Role.ROLE_USER));

        curationContainer = new SentenceIndex();

        WebMarkupContainer sidebarCell = new WebMarkupContainer("rightSidebar");
        sidebarCell.setOutputMarkupPlaceholderTag(true);
        // Override sidebar width from preferences
        sidebarCell.add(new AttributeModifier("style",
                () -> String.format("flex-basis: %d%%;",
                        getModelObject() != null
                                ? getModelObject().getPreferences().getSidebarSize()
                                : 10)));
        add(sidebarCell);

        curationView = new SentenceInfo();

        List<AnnotatorSegment> segments = new LinkedList<>();
        AnnotatorSegment annotatorSegment = new AnnotatorSegment();

        if (getModelObject() != null) {
            annotatorSegment
                    .setSelectionByUsernameAndAddress(annotationSelectionByUsernameAndAddress);
            annotatorSegment.setAnnotatorState(getModelObject());
            segments.add(annotatorSegment);
        }

        // update source list model only first time.
        sourceListModel = sourceListModel == null ? curationContainer.getSentenceInfos()
                : sourceListModel;

        annotatorsPanel = new AnnotatorsPanel("annotatorsPanel", new ListModel<>(segments));
        annotatorsPanel.setOutputMarkupPlaceholderTag(true);
        annotatorsPanel.add(visibleWhen(
                () -> getModelObject() != null && getModelObject().getDocument() != null));
        centerArea.add(annotatorsPanel);

        detailPanel = new AnnotationDetailEditorPanel("annotationDetailEditorPanel", this,
                getModel())
        {
            private static final long serialVersionUID = 2857345299480098279L;

            @Override
            protected void onAutoForward(AjaxRequestTarget aTarget)
            {
                annotationEditor.requestRender(aTarget);
            }

            @Override
            public CAS getEditorCas() throws IOException
            {
                return CurationPage.this.getEditorCas();
            }
        };
        detailPanel.add(enabledWhen(() -> getModelObject() != null //
                && getModelObject().getDocument() != null
                && !documentService
                        .getSourceDocument(getModelObject().getDocument().getProject(),
                                getModelObject().getDocument().getName())
                        .getState().equals(SourceDocumentState.CURATION_FINISHED)));
        sidebarCell.add(detailPanel);

        annotationEditor = new BratAnnotationEditor("annotationEditor", getModel(), detailPanel,
                this::getEditorCas);
        annotationEditor.setHighlightEnabled(false);
        annotationEditor.add(visibleWhen(
                () -> getModelObject() != null && getModelObject().getDocument() != null));
        annotationEditor.setOutputMarkupPlaceholderTag(true);
        // reset sentenceAddress and lastSentenceAddress to the orginal once
        centerArea.add(annotationEditor);

        // add container for sentences panel
        leftSidebar = new WebMarkupContainer("leftSidebar");
        leftSidebar.setOutputMarkupPlaceholderTag(true);
        leftSidebar.add(visibleWhen(
                () -> getModelObject() != null && getModelObject().getDocument() != null));
        add(leftSidebar);

        // add container for list of sentences panel
        sentenceList = new WebMarkupContainer("sentenceList");
        sentenceList.setOutputMarkupPlaceholderTag(true);
        sentenceList.add(new ListView<SentenceInfo>("sentence",
                LoadableDetachableModel.of(() -> curationContainer.getSentenceInfos()))
        {
            private static final long serialVersionUID = 8539162089561432091L;

            @Override
            protected void populateItem(ListItem<SentenceInfo> item)
            {
                item.add(new SentenceLink("sentenceNumber", item.getModel()));
            }
        });

        leftSidebar.add(sentenceList);
    }

    @OnEvent
    public void onAnnotationEvent(AnnotationEvent aEvent)
    {
        if (aEvent.getRequestTarget() != null) {
            updatePanel(aEvent.getRequestTarget(), curationContainer);
        }
    }

    /**
     * Re-render the document when the selection has changed.
     * 
     * @param aEvent
     *            the event.
     */
    @OnEvent
    public void onSelectionChangedEvent(SelectionChangedEvent aEvent)
    {
        actionRefreshDocument(aEvent.getRequestHandler());
    }

    public void onDocumentSelected(AjaxRequestTarget aTarget)
    {
        AnnotatorState state = getModelObject();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        /*
         * Changed for #152, getDocument was returning null even after opening a document Also,
         * surrounded following code into if block to avoid error.
         */
        if (state.getProject() == null) {
            setResponsePage(getApplication().getHomePage());
            return;
        }
        if (state.getDocument() != null) {
            try {
                documentService.createSourceDocument(state.getDocument());
                upgradeCasAndSave(state.getDocument(), username);

                actionLoadDocument(aTarget);
            }
            catch (Exception e) {
                LOG.error("Unable to load data", e);
                error("Unable to load data: " + ExceptionUtils.getRootCauseMessage(e));
            }
        }
    }

    @Override
    public IModel<List<DecoratedObject<Project>>> getAllowedProjects()
    {
        return new LoadableDetachableModel<List<DecoratedObject<Project>>>()
        {
            private static final long serialVersionUID = -2518743298741342852L;

            @Override
            protected List<DecoratedObject<Project>> load()
            {
                User user = userRepository
                        .get(SecurityContextHolder.getContext().getAuthentication().getName());
                List<DecoratedObject<Project>> allowedProject = new ArrayList<>();
                List<Project> projectsWithFinishedAnnos = projectService
                        .listProjectsWithFinishedAnnos();
                for (Project project : projectService.listProjects()) {
                    if (projectService.isCurator(project, user)) {
                        DecoratedObject<Project> dp = DecoratedObject.of(project);
                        if (projectsWithFinishedAnnos.contains(project)) {
                            dp.setColor("green");
                        }
                        else {
                            dp.setColor("red");
                        }
                        allowedProject.add(dp);
                    }
                }
                return allowedProject;
            }
        };
    }

    @Override
    public void setModel(IModel<AnnotatorState> aModel)
    {
        setDefaultModel(aModel);
    }

    @Override
    @SuppressWarnings("unchecked")
    public IModel<AnnotatorState> getModel()
    {
        return (IModel<AnnotatorState>) getDefaultModel();
    }

    @Override
    public void setModelObject(AnnotatorState aModel)
    {
        setDefaultModelObject(aModel);
    }

    @Override
    public AnnotatorState getModelObject()
    {
        return (AnnotatorState) getDefaultModelObject();
    }

    @Override
    public List<SourceDocument> getListOfDocs()
    {
        return curationDocumentService.listCuratableSourceDocuments(getModelObject().getProject());
    }

    /**
     * for the first time, open the <b>open document dialog</b>
     */
    @Override
    public void renderHead(IHeaderResponse response)
    {
        super.renderHead(response);

        String jQueryString = "";
        if (firstLoad) {
            jQueryString += "jQuery('#showOpenDocumentModal').trigger('click');";
            firstLoad = false;
        }
        response.render(OnLoadHeaderItem.forScript(jQueryString));
    }

    @Override
    public void writeEditorCas(CAS aCas) throws IOException, AnnotationException
    {
        ensureIsEditable();

        AnnotatorState state = getModelObject();
        curationDocumentService.writeCurationCas(aCas, state.getDocument(), true);

        // Update timestamp in state
        Optional<Long> diskTimestamp = curationDocumentService
                .getCurationCasTimestamp(state.getDocument());
        if (diskTimestamp.isPresent()) {
            state.setAnnotationDocumentTimestamp(diskTimestamp.get());
        }
    }

    private void upgradeCasAndSave(SourceDocument aDocument, String aUsername) throws IOException
    {
        User user = userRepository.get(aUsername);
        if (documentService.existsAnnotationDocument(aDocument, user)) {
            AnnotationDocument annotationDocument = documentService.getAnnotationDocument(aDocument,
                    user);
            try {
                CAS cas = documentService.readAnnotationCas(annotationDocument);
                annotationService.upgradeCas(cas, annotationDocument);
                documentService.writeAnnotationCas(cas, annotationDocument, false);
            }
            catch (Exception e) {
                // no need to catch, it is acceptable that no curation document
                // exists to be upgraded while there are annotation documents
            }
        }
    }

    @Override
    public void actionLoadDocument(AjaxRequestTarget aTarget)
    {
        actionLoadDocument(aTarget, 0);
    }

    /**
     * Open a document or to a different document. This method should be used only the first time
     * that a document is accessed. It reset the annotator state and upgrades the CAS.
     */
    private void actionLoadDocument(AjaxRequestTarget aTarget, int aFocus)
    {
        LOG.trace("BEGIN LOAD_DOCUMENT_ACTION at focus " + aFocus);

        AnnotatorState state = getModelObject();

        state.setUser(new User(CURATION_USER, Role.ROLE_USER));

        try {
            // Update source document state to CURRATION_INPROGRESS, if it was not
            // ANNOTATION_FINISHED
            if (!CURATION_FINISHED.equals(state.getDocument().getState())) {
                documentService.transitionSourceDocumentState(state.getDocument(),
                        ANNOTATION_IN_PROGRESS_TO_CURATION_IN_PROGRESS);
            }

            // Load constraints
            state.setConstraints(constraintsService.loadConstraints(state.getProject()));

            // Load user preferences
            loadPreferences();

            // if project is changed, reset some project specific settings
            if (currentprojectId != state.getProject().getId()) {
                state.clearRememberedFeatures();
                currentprojectId = state.getProject().getId();
            }

            CAS mergeCas = readOrCreateMergeCas(false, false);

            // (Re)initialize brat model after potential creating / upgrading CAS
            state.reset();

            // Initialize timestamp in state
            updateDocumentTimestampAfterWrite(state,
                    curationDocumentService.getCurationCasTimestamp(state.getDocument()));

            // Initialize the visible content
            state.moveToUnit(mergeCas, aFocus + 1, TOP);

            currentprojectId = state.getProject().getId();

            curationContainer = buildCurationContainer(state);
            detailPanel.reset(aTarget);
            init(aTarget, curationContainer);

            // Re-render whole page as sidebar size preference may have changed
            if (aTarget != null) {
                WicketUtil.refreshPage(aTarget, getPage());
            }
        }
        catch (Exception e) {
            handleException(aTarget, e);
        }

        LOG.trace("END LOAD_DOCUMENT_ACTION");
    }

    public CAS readOrCreateMergeCas(boolean aMergeIncompleteAnnotations, boolean aForceRecreateCas)
        throws IOException, UIMAException, ClassNotFoundException, AnnotationException
    {
        AnnotatorState state = getModelObject();

        List<AnnotationDocument> finishedAnnotationDocuments = documentService
                .listFinishedAnnotationDocuments(state.getDocument());

        if (finishedAnnotationDocuments.isEmpty()) {
            throw new IllegalStateException("This document has the state "
                    + state.getDocument().getState() + " but "
                    + "there are no finished annotation documents! This "
                    + "can for example happen when curation on a document has already started "
                    + "and afterwards all annotators have been remove from the project, have been "
                    + "disabled or if all were put back into " + AnnotationDocumentState.IN_PROGRESS
                    + " mode. It can "
                    + "also happen after importing a project when the users and/or permissions "
                    + "were not imported (only admins can do this via the projects page in the) "
                    + "administration dashboard and if none of the imported users have been "
                    + "enabled via the users management page after the import (also something "
                    + "that only administrators can do).");
        }

        AnnotationDocument randomAnnotationDocument = finishedAnnotationDocuments.get(0);

        Map<String, CAS> casses = readAllCasesSharedNoUpgrade(finishedAnnotationDocuments);
        CAS mergeCas = getMergeCas(state, state.getDocument(), casses, randomAnnotationDocument,
                true, aMergeIncompleteAnnotations, aForceRecreateCas);

        return mergeCas;
    }

    @Override
    public void actionRefreshDocument(AjaxRequestTarget aTarget)
    {
        try {
            updatePanel(aTarget, curationContainer);
            aTarget.add(centerArea.get(MID_NUMBER_OF_PAGES));
        }
        catch (Exception e) {
            handleException(aTarget, e);
        }
    }

    @Override
    protected void handleParameters(StringValue aDocumentParameter, StringValue aFocusParameter,
            boolean aLockIfPreset)
    {
        Project project = getProject();

        SourceDocument document = getDocumentFromParameters(project, aDocumentParameter);

        AnnotatorState state = getModelObject();

        // If there is no change in the current document, then there is nothing to do. Mind
        // that document IDs are globally unique and a change in project does not happen unless
        // there is also a document change.
        if (document != null && document.equals(state.getDocument()) && aFocusParameter != null
                && aFocusParameter.toInt(0) == state.getFocusUnitIndex()) {
            return;
        }

        // Check access to project
        if (project != null
                && !projectService.isCurator(project, userRepository.getCurrentUser())) {
            error("You have no permission to access project [" + project.getId() + "]");
            return;
        }

        // Update project in state
        // Mind that this is relevant if the project was specified as a query parameter
        // i.e. not only in the case that it was a URL fragment parameter.
        state.setProject(project);
        if (aLockIfPreset) {
            state.setProjectLocked(true);
        }

        // If we arrive here and the document is not null, then we have a change of document
        // or a change of focus (or both)
        if (document != null && !document.equals(state.getDocument())) {
            state.setDocument(document, getListOfDocs());
        }
    }

    @Override
    protected void updateDocumentView(AjaxRequestTarget aTarget, SourceDocument aPreviousDocument,
            StringValue aFocusParameter)
    {
        SourceDocument currentDocument = getModelObject().getDocument();
        if (currentDocument == null) {
            return;
        }

        // If we arrive here and the document is not null, then we have a change of document
        // or a change of focus (or both)

        // Get current focus unit from parameters
        int focus = 0;
        if (aFocusParameter != null) {
            focus = aFocusParameter.toInt(0);
        }
        // If there is no change in the current document, then there is nothing to do. Mind
        // that document IDs are globally unique and a change in project does not happen unless
        // there is also a document change.
        if (aPreviousDocument != null && aPreviousDocument.equals(currentDocument)
                && focus == getModelObject().getFocusUnitIndex()) {
            return;
        }
        // If we arrive here and the document is not null, then we have a change of document
        // or a change of focus (or both)
        if (aPreviousDocument == null || !aPreviousDocument.equals(currentDocument)) {
            actionLoadDocument(aTarget, focus);
        }
        else {
            try {
                getModelObject().moveToUnit(getEditorCas(), focus, TOP);
                actionRefreshDocument(aTarget);
            }
            catch (Exception e) {
                if (aTarget != null) {
                    aTarget.addChildren(getPage(), IFeedback.class);
                }
                LOG.info("Error reading CAS " + e.getMessage());
                error("Error reading CAS " + e.getMessage());
            }
        }
    }

    public class SentenceLink
        extends AjaxLink<SentenceInfo>
    {
        private static final long serialVersionUID = 4558300090461815010L;

        public SentenceLink(String aId, IModel<SentenceInfo> aModel)
        {
            super(aId, aModel);
            setBody(Model.of(aModel.getObject().getSentenceNumber().toString()));
        }

        @Override
        protected void onComponentTag(ComponentTag aTag)
        {
            super.onComponentTag(aTag);

            final SentenceInfo curationViewItem = getModelObject();

            // Is in focus?
            if (curationViewItem.getSentenceNumber() == CurationPage.this.getModelObject()
                    .getFocusUnitIndex()) {
                aTag.append("class", "current", " ");
            }

            // Agree or disagree?
            String cC = curationViewItem.getSentenceState().getValue();
            if (cC != null) {
                aTag.append("class", "disagree", " ");
            }
            else {
                aTag.append("class", "agree", " ");
            }

            // In range or not?
            if (curationViewItem.getSentenceNumber() >= firstVisibleUnitIndex
                    && curationViewItem.getSentenceNumber() <= lastVisibleUnitIndex) {
                aTag.append("class", "in-range", " ");
            }
            else {
                aTag.append("class", "out-range", " ");
            }
        }

        @Override
        protected void onAfterRender()
        {
            super.onAfterRender();

            // The sentence list is refreshed using AJAX. Unfortunately, the renderHead() method
            // of the AjaxEventBehavior created by AjaxLink does not seem to be called by Wicket
            // during an AJAX rendering, causing the sentence links to loose their functionality.
            // Here, we ensure that the callback scripts are attached to the sentence links even
            // during AJAX updates.
            if (isEnabledInHierarchy()) {
                RequestCycle.get().find(AjaxRequestTarget.class).ifPresent(_target -> {
                    for (AjaxEventBehavior b : getBehaviors(AjaxEventBehavior.class)) {
                        _target.appendJavaScript(
                                WicketUtil.wrapInTryCatch(b.getCallbackScript().toString()));
                    }
                });
            }
        }

        @Override
        public void onClick(AjaxRequestTarget aTarget)
        {
            final SentenceInfo curationViewItem = getModelObject();
            curationView = curationViewItem;
            firstVisibleUnitIndex = 0;
            try {
                AnnotatorState state = CurationPage.this.getModelObject();
                CAS cas = curationDocumentService.readCurationCas(state.getDocument());
                updateCurationView(curationContainer, curationViewItem, aTarget, cas);
                state.setFocusUnitIndex(curationViewItem.getSentenceNumber());
            }
            catch (IOException e) {
                error("Error: " + e.getMessage());
            }
        }
    }

    public void setModelObject(SentenceIndex aModel)
    {
        setDefaultModelObject(aModel);
    }

    private void updateCurationView(final SentenceIndex aCurationContainer,
            final SentenceInfo curationViewItem, AjaxRequestTarget aTarget, CAS aCas)
    {
        AnnotatorState state = CurationPage.this.getModelObject();
        state.getPagingStrategy().moveToOffset(state, aCas, curationViewItem.getBegin(), CENTERED);

        try {
            actionRefreshDocument(aTarget);
        }
        catch (Exception e) {
            handleException(aTarget, e);
        }
    }

    @Override
    public CAS getEditorCas() throws IOException
    {
        AnnotatorState state = CurationPage.this.getModelObject();

        if (state.getDocument() == null) {
            throw new IllegalStateException("Please open a document first!");
        }

        // If we have a timestamp, then use it to detect if there was a concurrent access
        verifyAndUpdateDocumentTimestamp(state,
                curationDocumentService.getCurationCasTimestamp(state.getDocument()));

        return curationDocumentService.readCurationCas(state.getDocument());
    }

    private void init(AjaxRequestTarget aTarget, SentenceIndex aCC)
        throws UIMAException, ClassNotFoundException, IOException
    {
        commonUpdate();

        annotatorsPanel.init(aTarget, getModelObject(), annotationSelectionByUsernameAndAddress,
                curationView);
    }

    private void updatePanel(AjaxRequestTarget aTarget, SentenceIndex aCC)
    {
        commonUpdate();

        // Render the main annotation editor (upper part)
        annotationEditor.requestRender(aTarget);

        // Render the user annotation segments (lower part)
        annotatorsPanel.requestUpdate(aTarget, getModelObject(),
                annotationSelectionByUsernameAndAddress, curationView);

        // Render the sentence list sidebar
        aTarget.add(sentenceList);
    }

    private void commonUpdate()
    {
        AnnotatorState state = CurationPage.this.getModelObject();

        curationView.setCurationBegin(state.getWindowBeginOffset());
        curationView.setCurationEnd(state.getWindowEndOffset());
        firstVisibleUnitIndex = state.getFirstVisibleUnitIndex();
        lastVisibleUnitIndex = state.getLastVisibleUnitIndex();
    }

    @Override
    public List<DecoratedObject<SourceDocument>> listAccessibleDocuments(Project aProject,
            User aUser)
    {
        final List<DecoratedObject<SourceDocument>> allSourceDocuments = new ArrayList<>();
        List<SourceDocument> sdocs = curationDocumentService.listCuratableSourceDocuments(aProject);

        for (SourceDocument sourceDocument : sdocs) {
            DecoratedObject<SourceDocument> dsd = DecoratedObject.of(sourceDocument);
            dsd.setLabel("%s (%s)", sourceDocument.getName(), sourceDocument.getState());
            dsd.setColor(sourceDocument.getState().getColor());
            allSourceDocuments.add(dsd);
        }
        return allSourceDocuments;
    }

    public SentenceIndex buildCurationContainer(AnnotatorState aState)
        throws UIMAException, ClassNotFoundException, IOException, AnnotationException
    {
        // get annotation documents
        List<AnnotationDocument> finishedAnnotationDocuments = documentService
                .listFinishedAnnotationDocuments(aState.getDocument());

        Map<String, CAS> casses = readAllCasesSharedNoUpgrade(finishedAnnotationDocuments);
        CAS mergeCas = getMergeCas(aState, aState.getDocument(), casses, null, false, false, false);

        int diffWindowStart = getFirstSentence(mergeCas).getBegin();
        int diffWindowEnd = mergeCas.getDocumentText().length();

        Map<Integer, Integer> segmentBeginEnd = new HashMap<>();
        Map<Integer, Integer> segmentNumber = new HashMap<>();
        Map<String, Map<Integer, Integer>> segmentAdress = new HashMap<>();
        updateSegment(aState, segmentBeginEnd, segmentNumber, segmentAdress, mergeCas,
                CURATION_USER, diffWindowStart, diffWindowEnd);

        segmentAdress.put(CURATION_USER, new HashMap<>());
        Type sentenceType = getType(mergeCas, Sentence.class);
        for (AnnotationFS s : selectCovered(mergeCas, sentenceType, diffWindowStart,
                diffWindowEnd)) {
            segmentAdress.get(CURATION_USER).put(s.getBegin(), getAddr(s));
        }

        List<DiffAdapter> adapters = getDiffAdapters(annotationService,
                aState.getAnnotationLayers());

        long diffStart = System.currentTimeMillis();
        LOG.debug("Calculating differences...");
        int count = 0;
        SentenceIndex curationContainer = new SentenceIndex();
        for (Integer begin : segmentBeginEnd.keySet()) {
            Integer end = segmentBeginEnd.get(begin);

            count++;
            if (count % 100 == 0) {
                LOG.debug("Processing differences: {} of {} sentences...", count,
                        segmentBeginEnd.size());
            }

            DiffResult diff = doDiffSingle(adapters, LINK_ROLE_AS_LABEL, casses, begin, end)
                    .toResult();

            SentenceInfo curationSegment = new SentenceInfo(begin, end, segmentNumber.get(begin));

            if (diff.hasDifferences() || !diff.getIncompleteConfigurationSets().isEmpty()) {
                // Is this confSet a diff due to stacked annotations (with same configuration)?
                boolean stackedDiff = false;

                stackedDiffSet: for (ConfigurationSet d : diff.getDifferingConfigurationSets()
                        .values()) {
                    for (Configuration c : d.getConfigurations()) {
                        if (c.getCasGroupIds().size() != d.getCasGroupIds().size()) {
                            stackedDiff = true;
                            break stackedDiffSet;
                        }
                    }
                }

                if (stackedDiff) {
                    curationSegment.setSentenceState(DISAGREE);
                }
                else if (!diff.getIncompleteConfigurationSets().isEmpty()) {
                    curationSegment.setSentenceState(DISAGREE);
                }
                else {
                    curationSegment.setSentenceState(AGREE);
                }
            }
            else {
                curationSegment.setSentenceState(AGREE);
            }

            for (String username : segmentAdress.keySet()) {
                curationSegment.getSentenceAddressByUserIndex().put(username,
                        segmentAdress.get(username).get(begin));
            }

            curationContainer.addSentenceInfo(curationSegment);
        }
        LOG.debug("Difference calculation completed in {}ms",
                (System.currentTimeMillis() - diffStart));

        return curationContainer;
    }

    public Map<String, CAS> readAllCasesSharedNoUpgrade(List<AnnotationDocument> aDocuments)
        throws IOException
    {
        Map<String, CAS> casses = new HashMap<>();
        for (AnnotationDocument annDoc : aDocuments) {
            String username = annDoc.getUser();
            CAS cas = documentService.readAnnotationCas(annDoc.getDocument(), username,
                    AUTO_CAS_UPGRADE, SHARED_READ_ONLY_ACCESS);
            casses.put(username, cas);
        }
        return casses;
    }

    /**
     * Fetches the CAS that the user will be able to edit. In AUTOMATION/CORRECTION mode, this is
     * the CAS for the CORRECTION_USER and in CURATION mode it is the CAS for the CURATION user.
     *
     * @param aState
     *            the model.
     * @param aDocument
     *            the source document.
     * @param aCasses
     *            the CASes.
     * @param aTemplate
     *            an annotation document which is used as a template for the new merge CAS.
     * @return the CAS.
     * @throws UIMAException
     *             hum?
     * @throws ClassNotFoundException
     *             hum?
     * @throws IOException
     *             if an I/O error occurs.
     * @throws AnnotationException
     *             hum?
     */
    public CAS getMergeCas(AnnotatorState aState, SourceDocument aDocument,
            Map<String, CAS> aCasses, AnnotationDocument aTemplate, boolean aUpgrade,
            boolean aMergeIncompleteAnnotations, boolean aForceRecreateCas)
        throws UIMAException, ClassNotFoundException, IOException, AnnotationException
    {
        if (aForceRecreateCas) {
            return initializeMergeCas(aState, aCasses, aTemplate, aMergeIncompleteAnnotations);
        }

        if (!curationDocumentService.existsCurationCas(aDocument)) {
            return initializeMergeCas(aState, aCasses, aTemplate, aMergeIncompleteAnnotations);
        }

        CAS mergeCas = curationDocumentService.readCurationCas(aDocument);
        if (aUpgrade) {
            curationDocumentService.upgradeCurationCas(mergeCas, aDocument);
            curationDocumentService.writeCurationCas(mergeCas, aDocument, true);
            updateDocumentTimestampAfterWrite(aState,
                    curationDocumentService.getCurationCasTimestamp(aState.getDocument()));
        }

        return mergeCas;
    }

    public CAS initializeMergeCas(AnnotatorState aState, Map<String, CAS> aCasses,
            AnnotationDocument aTemplate, boolean aMergeIncompleteAnnotations)
        throws ClassNotFoundException, UIMAException, IOException, AnnotationException
    {
        CAS mergeCas = createCurationCas(aState, aTemplate, aCasses, aState.getAnnotationLayers(),
                aMergeIncompleteAnnotations);
        updateDocumentTimestampAfterWrite(aState,
                curationDocumentService.getCurationCasTimestamp(aState.getDocument()));

        return mergeCas;
    }

    /**
     * Puts CASes into a list and get a random annotation document that will be used as a base for
     * the diff.
     */
    private void updateSegment(AnnotatorState aBratAnnotatorModel,
            Map<Integer, Integer> aIdxSentenceBeginEnd,
            Map<Integer, Integer> aIdxSentenceBeginNumber,
            Map<String, Map<Integer, Integer>> aSegmentAdress, CAS aCas, String aUsername,
            int aWindowStart, int aWindowEnd)
    {
        // Get the number of the first sentence - instead of fetching the number over and over
        // we can just increment this one.
        int sentenceNumber = WebAnnoCasUtil.getSentenceNumber(aCas, aWindowStart);

        aSegmentAdress.put(aUsername, new HashMap<>());
        Type sentenceType = CasUtil.getType(aCas, Sentence.class);
        for (AnnotationFS sentence : selectCovered(aCas, sentenceType, aWindowStart, aWindowEnd)) {
            aIdxSentenceBeginEnd.put(sentence.getBegin(), sentence.getEnd());
            aIdxSentenceBeginNumber.put(sentence.getBegin(), sentenceNumber);
            aSegmentAdress.get(aUsername).put(sentence.getBegin(), getAddr(sentence));
            sentenceNumber += 1;
        }
    }

    @Deprecated
    public static List<Type> getEntryTypes(CAS aMergeCas, List<AnnotationLayer> aLayers,
            AnnotationSchemaService aAnnotationService)
    {
        List<Type> entryTypes = new LinkedList<>();

        for (AnnotationLayer layer : aLayers) {
            if (layer.getName().equals(Token.class.getName())) {
                continue;
            }
            if (layer.getType().equals(WebAnnoConst.CHAIN_TYPE)) {
                continue;
            }
            entryTypes.add(aAnnotationService.getAdapter(layer).getAnnotationType(aMergeCas));
        }
        return entryTypes;
    }

    /**
     * For the first time a curation page is opened, create a MergeCas that contains only agreeing
     * annotations Using the CAS of the curator user.
     *
     * @param aState
     *            the annotator state
     * @param aRandomAnnotationDocument
     *            an annotation document.
     * @param aCasses
     *            the CASes
     * @param aAnnotationLayers
     *            the layers.
     * @return the CAS.
     * @throws IOException
     *             if an I/O error occurs.
     */
    private CAS createCurationCas(AnnotatorState aState,
            AnnotationDocument aRandomAnnotationDocument, Map<String, CAS> aCasses,
            List<AnnotationLayer> aAnnotationLayers, boolean aMergeIncompleteAnnotations)
        throws IOException, UIMAException, AnnotationException
    {
        Validate.notNull(aState, "State must be specified");
        Validate.notNull(aRandomAnnotationDocument, "Annotation document must be specified");

        // We need a modifiable copy of some annotation document which we can use to initialize
        // the curation CAS. This is an exceptional case where BYPASS is the correct choice
        CAS mergeCas = documentService.readAnnotationCas(aRandomAnnotationDocument,
                UNMANAGED_ACCESS);

        List<DiffAdapter> adapters = getDiffAdapters(annotationService,
                aState.getAnnotationLayers());

        DiffResult diff;
        try (StopWatch watch = new StopWatch(LOG, "CasDiff")) {
            diff = doDiffSingle(adapters, LINK_ROLE_AS_LABEL, aCasses, 0,
                    mergeCas.getDocumentText().length()).toResult();
        }

        try (StopWatch watch = new StopWatch(LOG, "CasMerge")) {
            CasMerge casMerge = new CasMerge(annotationService);
            casMerge.setMergeIncompleteAnnotations(aMergeIncompleteAnnotations);
            casMerge.reMergeCas(diff, aState.getDocument(), aState.getUser().getUsername(),
                    mergeCas, aCasses);
        }

        curationDocumentService.writeCurationCas(mergeCas, aRandomAnnotationDocument.getDocument(),
                false);

        return mergeCas;
    }
}
