/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package edu.musc.tbic.readers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
//import org.apache.uima.jcas.tcas.DocumentAnnotation;
import org.apache.uima.resource.ResourceConfigurationException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.FileUtils;
import org.apache.uima.util.Progress;
import org.apache.uima.util.ProgressImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import edu.musc.tbic.uima.FeatureGen;


/**
 * A simple collection reader that reads documents from a directory in the filesystem. It can be
 * configured with the following parameters:
 * <ul>
 * <li><code>InputDirectory</code> - path to directory containing files</li>
 * <li><code>Encoding</code> (optional) - character encoding of the input files</li>
 * <li><code>Language</code> (optional) - language of the input documents</li>
 * </ul>
 * 
 * 
 */
public class FileSystemCollectionReader extends CollectionReader_ImplBase {

    private static final Logger mLogger = LoggerFactory.getLogger( FeatureGen.class );

    /**
     * Name of configuration parameter that must be set to the path of a directory containing input
     * files.
     */
    public static final String PARAM_INPUTDIR = "InputDirectory";

    /**
     * Name of configuration parameter that must be set to the path of a directory containing
     * structured annotation input files.
     */
    public static final String PARAM_ANNOTDIR = "AnnotationDirectory";
    private File mAnnotationDirectory;

    /**
     * Name of configuration parameter that must be set to the file suffix for
     * structured annotation input files.
     */
    public static final String PARAM_ANNOTSUFFIX = "AnnotationSuffix";
    private String mAnnotationSuffix;

    /**
     * Name of configuration parameter that contains the character encoding used by the input files.
     * If not specified, the default system encoding will be used.
     */
    public static final String PARAM_ENCODING = "Encoding";

    /**
     * Name of optional configuration parameter that contains the language of the documents in the
     * input directory. If specified this information will be added to the CAS.
     */
    public static final String PARAM_LANGUAGE = "Language";

    /**
     * Name of optional configuration parameter that indicates including
     * the subdirectories (recursively) of the current input directory.
     */
    public static final String PARAM_SUBDIR = "BrowseSubdirectories";

    /** The m files. */
    private ArrayList<File> mFiles;

    /** The m encoding. */
    private String mEncoding;

    /** The m language. */
    private String mLanguage;

    /** The m recursive. */
    private Boolean mRecursive;

    /** The m current index. */
    private int mCurrentIndex;

    private DocumentBuilderFactory mFactory;
    private DocumentBuilder mDomBuilder;

    /**
     * Initialize.
     *
     * @throws ResourceInitializationException the resource initialization exception
     * @see org.apache.uima.collection.CollectionReader_ImplBase#initialize()
     */
    public void initialize() throws ResourceInitializationException {
        File directory = new File(((String) getConfigParameterValue(PARAM_INPUTDIR)).trim());
        if( getConfigParameterValue(PARAM_ANNOTDIR) == null ){
            mAnnotationDirectory = null;
        } else {
            mAnnotationDirectory = new File(((String) getConfigParameterValue(PARAM_ANNOTDIR)).trim());
        }
        if( getConfigParameterValue(PARAM_ANNOTSUFFIX) == null ){
            mAnnotationSuffix = "";
        } else {
            mAnnotationSuffix = ((String) getConfigParameterValue(PARAM_ANNOTSUFFIX)).trim();
        }
        mEncoding  = (String) getConfigParameterValue(PARAM_ENCODING);
        mLanguage  = (String) getConfigParameterValue(PARAM_LANGUAGE);
        mRecursive = (Boolean) getConfigParameterValue(PARAM_SUBDIR);
        if (null == mRecursive) { // could be null if not set, it is optional
            mRecursive = Boolean.FALSE;
        }
        mCurrentIndex = 0;

        // if input directory does not exist or is not a directory, throw exception
        if (!directory.exists() || !directory.isDirectory()) {
            throw new ResourceInitializationException(ResourceConfigurationException.DIRECTORY_NOT_FOUND,
                    new Object[] { PARAM_INPUTDIR, this.getMetaData().getName(), directory.getPath() });
        }

        //Get the DOM Builder Factory
        mFactory = DocumentBuilderFactory.newInstance();
        //Get the DOM Builder
        try {
            mDomBuilder = mFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // get list of files in the specified directory, and subdirectories if the
        // parameter PARAM_SUBDIR is set to True
        mFiles = new ArrayList<>();
        addFilesFromDir(directory);
    }

    /**
     * This method adds files in the directory passed in as a parameter to mFiles.
     * If mRecursive is true, it will include all files in all
     * subdirectories (recursively), as well. 
     *
     * @param dir the dir
     */
    private void addFilesFromDir(File dir) {
        File[] files = dir.listFiles();
        for( int i = 0 ; i < files.length ; i++ ) {
            if( ! files[ i ].isDirectory() &&
                    ! files[ i ].getName().endsWith( ".DS_Store" ) &&
                    ( mAnnotationSuffix.equals( "" ) ||
                            ! files[ i ].getName().endsWith( mAnnotationSuffix ) ) ){
                if( mAnnotationDirectory == null ){
                    // With no annotation directory defined, the existence of a file
                    // on its own is enough to add it to the queue
                    mFiles.add( files[ i ] );
                } else {
                    // When there is an annotation directory, we need to make sure
                    // every text file has an associated annotation file
                    File annotationFile = null;
                    if( mAnnotationSuffix.equals( ".ann" ) ||
                            mAnnotationSuffix.equals( ".ast" ) ){
                        String txtFilename = files[ i ].getName();
                        annotationFile = new File( mAnnotationDirectory , 
                                txtFilename.substring( 0 ,
                                        txtFilename.length() - 4 ) + mAnnotationSuffix );
                    } else if( mAnnotationSuffix.equals( "ann" ) ||
                            mAnnotationSuffix.equals( "ast" ) ){
                        String txtFilename = files[ i ].getName();
                        annotationFile = new File( mAnnotationDirectory , 
                                txtFilename.substring( 0 ,
                                        txtFilename.length() - 3 ) + mAnnotationSuffix );
                    }
                    if( annotationFile != null &&
                            annotationFile.exists() ){
                        mFiles.add( files[ i ] );
                    }
                }
            } else if( mRecursive ){
                addFilesFromDir( files[ i ] );
            }
        }
    }

    /**
     * Checks for next.
     *
     * @return true, if successful
     * @see org.apache.uima.collection.CollectionReader#hasNext()
     */
    public boolean hasNext() {
        return mCurrentIndex < mFiles.size();
    }

    /**
     * Gets the next.
     *
     * @param aCAS the a CAS
     * @throws IOException Signals that an I/O exception has occurred.
     * @throws CollectionException the collection exception
     * @see org.apache.uima.collection.CollectionReader#getNext(org.apache.uima.cas.CAS)
     */
    public void getNext(CAS aCAS) throws IOException, CollectionException {
        JCas jcas;
        try {
            jcas = aCAS.getJCas();
        } catch (CASException e) {
            throw new CollectionException(e);
        }

        // open input stream to file
        File file = (File) mFiles.get(mCurrentIndex++);
        String text = "";

        if( file.getName().endsWith( ".txt" ) ){
            try{
                text = FileUtils.file2String(file, mEncoding);
            } catch ( FileNotFoundException e ){
                mLogger.warn( "Skipping file due to FileNotFoundException: " + file.getName() );
                return;
            }
            //
            if( mAnnotationDirectory != null ){
                if( mAnnotationSuffix.equals( ".ann" ) ||
                        mAnnotationSuffix.equals( "ann" ) ){
                    mLogger.debug( "Skipping brat files" );
                } else if( mAnnotationSuffix.equals( ".ast" ) ||
                        mAnnotationSuffix.equals( "ast" ) ){
                    mLogger.debug( "Skipping assertion files" );
                }
            }
        } else if( file.getName().endsWith( ".xmi" ) ){
            Document document;
            try {
                document = (Document) mDomBuilder.parse( file );
            } catch (SAXException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
                return;
            }

            //System.out.println("Root element :" + document.getDocumentElement().getNodeName());
            NodeList nList = document.getElementsByTagName("cas:Sofa");
            if( nList.getLength() > 1 ){
                System.err.println( "Warning:  More than one <cas:Sofa> element detected. "
                        + "Concatenating all element contents." );
            }
            for (int temp = 0; temp < nList.getLength(); temp++) {
                Node nNode = nList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    text += eElement.getAttribute( "sofaString" );
                }
            }
            //
            NodeList problemList = document.getElementsByTagName( "custom:Problems" );
            for( int temp = 0; 
                    temp < problemList.getLength(); 
                    temp++ ) {
                Node problemNode = problemList.item( temp );
                if( problemNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) problemNode;
                    int beginOffset = Integer.parseInt( eElement.getAttribute( "begin" ) );
                    int endOffset = Integer.parseInt( eElement.getAttribute( "end" ) );
                    String negatedStatus = eElement.getAttribute( "negated" );
                    IdentifiedAnnotation iaConcept = new IdentifiedAnnotation( jcas ,
                            beginOffset ,
                            endOffset );
                    if( negatedStatus.equalsIgnoreCase( "false" ) ){
                        iaConcept.setPolarity( 1 );
                    } else if( negatedStatus.equalsIgnoreCase( "true" ) ){
                        iaConcept.setPolarity( -1 );
                    } else {
                        iaConcept.setPolarity( 1 );
                    }
                    iaConcept.addToIndexes();
                }
            }
        }

        // put document in CAS
        jcas.setDocumentText(text);

        // set language if it was explicitly specified as a configuration parameter
        if (mLanguage != null) {
            jcas.setDocumentLanguage(mLanguage);
        }

        // Also store location of source document in CAS. This information is critical
        // if CAS Consumers will need to know where the original document contents are located.
        // For example, the Semantic Search CAS Indexer writes this information into the
        // search index that it creates, which allows applications that use the search index to
        // locate the documents that satisfy their semantic queries.
        SourceDocumentInformation srcDocInfo = new SourceDocumentInformation(jcas);
        // TODO - this may be a more appropriate fix for the original, deprecated setUri command
        // srcDocInfo.setUri( file.getAbsoluteFile().toString() );
        srcDocInfo.setUri( file.getName() );
        srcDocInfo.setOffsetInSource(0);
        srcDocInfo.setDocumentSize((int) file.length());
        srcDocInfo.setLastSegment(mCurrentIndex == mFiles.size());
        srcDocInfo.addToIndexes();
    }

    /**
     * Close.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     * @see org.apache.uima.collection.base_cpm.BaseCollectionReader#close()
     */
    public void close() throws IOException {
    }

    /**
     * Gets the progress.
     *
     * @return the progress
     * @see org.apache.uima.collection.base_cpm.BaseCollectionReader#getProgress()
     */
    public Progress[] getProgress() {
        return new Progress[] { new ProgressImpl(mCurrentIndex, mFiles.size(), Progress.ENTITIES) };
    }

    /**
     * Gets the total number of documents that will be returned by this collection reader. This is not
     * part of the general collection reader interface.
     * 
     * @return the number of documents in the collection
     */
    public int getNumberOfDocuments() {
        return mFiles.size();
    }

}
