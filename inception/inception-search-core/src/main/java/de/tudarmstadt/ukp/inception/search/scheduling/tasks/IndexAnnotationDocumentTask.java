/*
 * Copyright 2017
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * 
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
package de.tudarmstadt.ukp.inception.search.scheduling.tasks;

import org.springframework.beans.factory.annotation.Autowired;

import de.tudarmstadt.ukp.clarin.webanno.api.dao.casstorage.CasStorageSession;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.inception.scheduling.Task;
import de.tudarmstadt.ukp.inception.search.SearchService;

/**
 * (Re)indexes the annotation document for a specific user.
 */
public class IndexAnnotationDocumentTask
    extends IndexingTask_ImplBase
{
    private @Autowired SearchService searchService;

    public IndexAnnotationDocumentTask(AnnotationDocument aAnnotationDocument, String aTrigger,
            byte[] aBinaryCas)
    {
        super(aAnnotationDocument, aTrigger, aBinaryCas);
    }

    @Override
    public void execute()
    {
        try (CasStorageSession session = CasStorageSession.open()) {
            searchService.indexDocument(super.getAnnotationDocument(), super.getBinaryCas());
        }
    }

    @Override
    public boolean matches(Task aTask)
    {
        if (!(aTask instanceof IndexAnnotationDocumentTask)) {
            return false;
        }

        return getAnnotationDocument().getId() == ((IndexAnnotationDocumentTask) aTask)
                .getAnnotationDocument().getId();
    }

}