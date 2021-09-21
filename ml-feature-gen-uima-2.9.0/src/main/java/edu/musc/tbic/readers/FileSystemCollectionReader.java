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
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.collection.CollectionReader_ImplBase;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
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
                    HashMap<String, ArrayList<Integer>> annotationMap = new HashMap<>();
                    HashMap<String, ArrayList<String>> attributeMap = new HashMap<>();
                    String txtFilename = file.getName();
                    File annotationFile = new File( mAnnotationDirectory , 
                            txtFilename.substring( 0 ,
                                    txtFilename.length() - mAnnotationSuffix.length() ) + mAnnotationSuffix );
                    Reader fileReader = new FileReader( annotationFile );
                    CSVParser astParser = CSVParser.parse( fileReader , 
                            CSVFormat.DEFAULT.withDelimiter( '\t' ).withHeader( "id" , 
                                    "secondCol" , 
                                    "thirdCol" ) );
                    Iterator<CSVRecord> recordIterator = astParser.iterator();
                    while( recordIterator.hasNext() ){
                        CSVRecord astRecord = recordIterator.next();
                        String conceptId = astRecord.get( "id" );
                        // Skip all the non-text span and non-attribute entries
                        if( conceptId.startsWith( "T" ) ) {
                            String conceptTypeOffset = astRecord.get( "secondCol" );
                            Pattern offsetPattern = Pattern.compile( "(.*) ([0-9]+) ([0-9]+)(;([0-9]+) ([0-9]+))*$" );
                            Matcher matcher = offsetPattern.matcher( conceptTypeOffset );
                            String conceptType = "";
                            int beginOffset = -1;
                            int endOffset = -1;
                            if( matcher.find() ) {
                                conceptType = matcher.group( 1 );
                                if( conceptType.equalsIgnoreCase( "Problem" ) ){
                                    if( annotationMap.get( conceptId ) == null) {
                                        annotationMap.put( conceptId , new ArrayList<Integer>() );
                                        beginOffset = Integer.parseInt( matcher.group( 2 ) );
                                        endOffset = Integer.parseInt( matcher.group( 3 ) );
                                        annotationMap.get( conceptId ).add( beginOffset );
                                        annotationMap.get( conceptId ).add( endOffset );
                                        if( matcher.group( 6 ) != null ){
                                            // TODO - we'll preserve the discontinuous annotation spans
                                            // until later even through we don't technically process them.
                                            // 0 5;13 23 is treated as the continuous span 0 23
                                            beginOffset = Integer.parseInt( matcher.group( 5 ) );
                                            endOffset = Integer.parseInt( matcher.group( 6 ) );
                                            annotationMap.get( conceptId ).add( beginOffset );
                                            annotationMap.get( conceptId ).add( endOffset );
                                        }
                                    } else {
                                        mLogger.warn( "Annotation span " + conceptId + " is listed twice. Skipping second entry." );
                                    }
                                }
                            }
                        } else if( conceptId.startsWith( "A" ) ){
                            String conceptAttributeId = astRecord.get( "secondCol" );
                            Pattern attributePattern = Pattern.compile( "(.*) ([TRE][0-9]+)( (.*))?$" );
                            Matcher matcher = attributePattern.matcher( conceptAttributeId );
                            String conceptAttribute = "";
                            String spanId = "";
                            if( matcher.find() ) {
                                conceptAttribute = matcher.group( 1 );
                                spanId = matcher.group( 2 );
                                // TODO - allow this list of attributes to be provided via a config file
                                if( spanId.startsWith( "T" ) &&
                                    ( conceptAttribute.equalsIgnoreCase( "Conditional" ) ||
                                      conceptAttribute.equalsIgnoreCase( "Generic" ) ||
                                      conceptAttribute.equalsIgnoreCase( "Historical" ) ||
                                      conceptAttribute.equalsIgnoreCase( "Negated" ) ||
                                      conceptAttribute.equalsIgnoreCase( "NotPatient" ) ||
                                      conceptAttribute.equalsIgnoreCase( "Uncertain" ) ) ){
                                    if( attributeMap.get( spanId ) == null) {
                                        attributeMap.put( spanId , new ArrayList<String>() );
                                    }
                                    attributeMap.get( spanId ).add( conceptAttribute );
                                }
                            }
                        }
                    }
                    for( HashMap.Entry<String, ArrayList<Integer>> annotationInstance :
                         annotationMap.entrySet() ) {
                        String spanId = annotationInstance.getKey();
                        ArrayList<Integer> offsetList = annotationInstance.getValue();
                        Integer beginOffset = offsetList.get( 0 );
                        Integer endOffset = offsetList.get( offsetList.size() - 1 );
                        IdentifiedAnnotation iaConcept = new IdentifiedAnnotation( jcas ,
                                beginOffset ,
                                endOffset );
                        iaConcept.setPolarity( 1 );
                        iaConcept.setSubject( "patient" );
                        iaConcept.setConditional( false );
                        iaConcept.setGeneric( false );
                        iaConcept.setHistoryOf( 0 );
                        iaConcept.setUncertainty( 0 );
                        if( attributeMap.get( spanId ) != null) {
                            ArrayList<String> attributeList = attributeMap.get( spanId );
                            for( int i = 0 ; i < attributeList.size(); i++ ){
                                String attributeFlag = attributeList.get( i );
                                if( attributeFlag.equalsIgnoreCase( "Conditional" ) ){
                                    iaConcept.setConditional( true );
                                } else if( attributeFlag.equalsIgnoreCase( "Generic" ) ){
                                  iaConcept.setGeneric( true );
                                } else if( attributeFlag.equalsIgnoreCase( "Historical" ) ){
                                  iaConcept.setHistoryOf( 1 );
                                } else if( attributeFlag.equalsIgnoreCase( "Negated" ) ){
                                    iaConcept.setPolarity( -1 );
                                } else if( attributeFlag.equalsIgnoreCase( "NotPatient" ) ){
                                    iaConcept.setSubject( "not patient" );
                                } else if( attributeFlag.equalsIgnoreCase( "Uncertain" ) ){
                                    iaConcept.setUncertainty( 1 );
                                } else {
                                    mLogger.warn( "Unrecognized attribute flag: " + attributeFlag );
                                }
                            }
                        }
                        iaConcept.addToIndexes();
                    }
                } else if( mAnnotationSuffix.equals( ".ast" ) ||
                        mAnnotationSuffix.equals( "ast" ) ){
                    String lines[] = text.split("\\n");
                    ArrayList<Integer> priorOffset = new ArrayList<>();
                    priorOffset.add( 0 );
                    for( int i = 1 ; i < lines.length ; i++ ){
                        priorOffset.add( priorOffset.get( i - 1 ) + 1 + lines[ i - 1 ].length() );
                    }
                    String txtFilename = file.getName();
                    File annotationFile = new File( mAnnotationDirectory , 
                            txtFilename.substring( 0 ,
                                    txtFilename.length() - mAnnotationSuffix.length() ) + mAnnotationSuffix );
                    Reader fileReader = new FileReader( annotationFile );
                    CSVParser astParser = CSVParser.parse( fileReader , 
                            CSVFormat.DEFAULT.withDelimiter( '|' ).withHeader( "span" , 
                                    "EmptyLeft" ,
                                    "type" , 
                                    "EmptyRight" ,
                                    "assertion" ) );
                    Iterator<CSVRecord> recordIterator = astParser.iterator();
                    while( recordIterator.hasNext() ){
                        CSVRecord astRecord = recordIterator.next();
                        String conceptSpan = astRecord.get( "span" );
                        String conceptType = astRecord.get( "type" );
                        String conceptAssertion = astRecord.get( "assertion" );
                        // Skip all the non-problems (which should be none)
                        if( ! conceptType.equalsIgnoreCase( "t=\"problem\"" ) ){
                            continue;
                        }
                        Pattern spanPattern = Pattern.compile( ".* ([0-9]+):([0-9]+) ([0-9]+):([0-9]+)$" );
                        Matcher matcher = spanPattern.matcher( conceptSpan );
                        int pos = 0;
                        int beginLine = -1;
                        int beginToken = -1;
                        int endLine = -1;
                        int endToken = -1;
                        if( matcher.find() ) {
                            beginLine = Integer.parseInt( matcher.group( 1 ) );
                            beginToken = Integer.parseInt( matcher.group( 2 ) );
                            endLine = Integer.parseInt( matcher.group( 3 ) );
                            endToken = Integer.parseInt( matcher.group( 4 ) );
                        }
                        if( beginLine == -1 ){
                            continue;
                        }
                        String tokens[] = lines[ beginLine - 1 ].split( " " );
                        ArrayList<Integer> tokenOffset = new ArrayList<>();
                        tokenOffset.add( 0 );
                        for( int i = 1 ; i < tokens.length ; i++ ){
                            tokenOffset.add( tokenOffset.get( i - 1 ) + 1 + tokens[ i - 1 ].length() );
                        }
                        int beginOffset = priorOffset.get( beginLine - 1 ) + tokenOffset.get( beginToken );
                        if( beginLine < endLine ){
                            tokens = lines[ endLine - 1 ].split( " " );
                            tokenOffset.clear();
                            tokenOffset = new ArrayList<>();
                            tokenOffset.add( 0 );
                            for( int i = 1 ; i < tokens.length ; i++ ){
                                tokenOffset.add( tokenOffset.get( i - 1 ) + 1 + tokens[ i - 1 ].length() );
                            }
                        }
                        int endOffset = priorOffset.get( endLine - 1 );
                        if( endToken + 1 == tokens.length ){
                            endOffset += lines[ endLine - 1 ].length();
                        } else {
                            endOffset += tokenOffset.get( endToken + 1 ) - 1;
                        }
                        IdentifiedAnnotation iaConcept = new IdentifiedAnnotation( jcas ,
                                beginOffset ,
                                endOffset );
                        iaConcept.setPolarity( 1 );
                        iaConcept.setSubject( "patient" );
                        iaConcept.setConditional( false );
                        iaConcept.setGeneric( false );
                        iaConcept.setHistoryOf( 0 );
                        iaConcept.setUncertainty( 0 );
                        if( conceptAssertion.equalsIgnoreCase( "a=\"present\"" ) ){
                            iaConcept.setPolarity( 1 );
                        } else if( conceptAssertion.equalsIgnoreCase( "a=\"absent\"" ) ){
                            iaConcept.setPolarity( -1 );
                        } else if( conceptAssertion.equalsIgnoreCase( "a=\"associated_with_someone_else\"" ) ){
                            iaConcept.setSubject( "not patient" );
                        } else if( conceptAssertion.equalsIgnoreCase( "a=\"conditional\"" ) ){
                            iaConcept.setConditional( true );
                        } else if( conceptAssertion.equalsIgnoreCase( "a=\"possible\"" ) ){
                            iaConcept.setUncertainty( 1 );
                        } else if( conceptAssertion.equalsIgnoreCase( "a=\"hypothetical\"" ) ){
                            iaConcept.setUncertainty( 1 );
                        } else {
                            mLogger.warn( "Unrecognized assertion value: " + conceptAssertion );
                        }
                        iaConcept.addToIndexes();
                    }
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
                    IdentifiedAnnotation iaConcept = new IdentifiedAnnotation( jcas ,
                            beginOffset ,
                            endOffset );
                    iaConcept.setPolarity( 1 );
                    iaConcept.setSubject( "patient" );
                    iaConcept.setConditional( false );
                    iaConcept.setGeneric( false );
                    iaConcept.setHistoryOf( 0 );
                    iaConcept.setUncertainty( 0 );
                    String conditionalStatus = eElement.getAttribute( "conditional" );
                    String genericStatus = eElement.getAttribute( "generic" );
                    String historicalStatus = eElement.getAttribute( "historical" );
                    String negatedStatus = eElement.getAttribute( "negated" );
                    String notPatientStatus = eElement.getAttribute( "not_patient" );
                    String uncertainStatus = eElement.getAttribute( "uncertain" );
                    if( conditionalStatus.equalsIgnoreCase( "true" ) ){
                        iaConcept.setConditional( true );
                    }
                    if( genericStatus.equalsIgnoreCase( "true" ) ){
                        iaConcept.setGeneric( true );
                    }
                    if( historicalStatus.equalsIgnoreCase( "true" ) ){
                        iaConcept.setHistoryOf( 1 );
                    }
                    if( negatedStatus.equalsIgnoreCase( "true" ) ){
                        iaConcept.setPolarity( -1 );
                    }
                    if( notPatientStatus.equalsIgnoreCase( "true" ) ){
                        iaConcept.setSubject( "not patient" );
                    }
                    if( uncertainStatus.equalsIgnoreCase( "true" ) ){
                        iaConcept.setUncertainty( 1 );
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
