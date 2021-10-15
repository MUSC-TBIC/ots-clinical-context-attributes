package edu.musc.tbic.context;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.musc.tbic.uima.FeatureGen;
import edu.utah.bmi.nlp.context.ConText;
import edu.musc.tbic.uima.NoteSection;

import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import edu.musc.tbic.omop_cdm.Note_Nlp_TableProperties;

public class ConTextAnnotator extends JCasAnnotator_ImplBase {

    private static final Logger mLogger = LoggerFactory.getLogger( FeatureGen.class );

    private ConText _contextAnalyzer;

    /**
     * Name of configuration parameter that must be set to the full type description for sentences
     */
    public static final String PARAM_SENTENCETYPE = "SentenceType";
    @ConfigurationParameter( name = PARAM_SENTENCETYPE , 
            description = "Full type description for sentences" , 
            mandatory = false )
    private String mSentenceType;

    public static final String PARAM_CLASSMAP = "ClassMap";

    static String mClassMap;

    static HashMap<String, String> mClassHashMapIndex2Str;
    static HashMap<String, String> mClassHashMapStr2Index;

    int [][] mConfusionMatrix;

    /**
     * Initialization before processing the CAS (load parameters from the configuration file)
     */
    public void initialize(UimaContext aContext) throws ResourceInitializationException {
        super.initialize(aContext);

        if( aContext.getConfigParameterValue( "SentenceType" ) == null ){
            mSentenceType = "org.apache.ctakes.typesystem.type.textspan.Sentence";
        } else {
            mSentenceType = (String) aContext.getConfigParameterValue( "SentenceType" );
        }

        try
        {
            //initialize sets of semantic types to distinct drugs and diseases
            mLogger.debug("Loading ConText Analyzer");

            //instantiate new context analyzer
            _contextAnalyzer = ConText.getAnalyzer(aContext);

            mLogger.debug("ConText Analyzer loaded");

        }
        catch (Exception ace){
            throw new ResourceInitializationException(ace);
        }

        mClassMap = null;

        mClassHashMapIndex2Str = new HashMap<>();
        mClassHashMapStr2Index = new HashMap<>();

        mClassMap = (String) aContext.getConfigParameterValue(PARAM_CLASSMAP);
        readClassMapFile();
        mConfusionMatrix = new int[ mClassHashMapIndex2Str.size() + 1 ][ mClassHashMapIndex2Str.size() + 1 ];

    }

    public static void readClassMapFile() {

        String str = "";
        {
            BufferedReader txtin = null;
            try {

                txtin = new BufferedReader( new FileReader( mClassMap ) );
                while ((str = txtin.readLine()) != null) {
                    String strA[] = str.split(" ");
                    //map.put(strA[0], strA[1]);
                    // File:  present 1
                    // Map:   [ "present" ] -> "1"
                    mClassHashMapStr2Index.put( strA[ 0 ] , strA[ 1 ] );
                    // Map:   [ "1" ] -> "present"
                    mClassHashMapIndex2Str.put( strA[ 1 ] , strA[ 0 ] );
                    mLogger.debug( "Class " + strA[ 0 ] + " -> " + strA[ 1 ] );
                }

            } catch (Exception ex) {
                System.err.println(ex.getMessage());
            } finally {
                try {
                    txtin.close();
                } catch (Exception ex) {
                }
            }
        }
    }

    private void processSection( JCas cas , 
            String currentSectionId ,
            int lastSectionBegin , 
            int nextSectionBegin ) throws Exception{
        // Go through all the sentences using select since subiterator doesn't play nicely with uimaFIT
        // cf. https://issues.apache.org/jira/browse/CTAKES-16
        Collection<Sentence> sentences = JCasUtil.select( cas , Sentence.class );

        ArrayList<Note_Nlp_TableProperties> concepts = new ArrayList<Note_Nlp_TableProperties>();

        int sentenceCount = 0;
        int conceptCount = 0;
        for( Sentence current_sentence : sentences ){
            if( current_sentence.getBegin() < lastSectionBegin || 
                    ( nextSectionBegin > -1 &&
                            current_sentence.getBegin() >= nextSectionBegin ) ) {
                continue;
            }
            sentenceCount++;

            // Go through all the concepts using select since subiterator doesn't play nicely with uimaFIT
            // cf. https://issues.apache.org/jira/browse/CTAKES-16
            Collection<IdentifiedAnnotation> original_concepts = JCasUtil.select( cas , IdentifiedAnnotation.class );
            for( IdentifiedAnnotation original_concept : original_concepts ){
                if( original_concept.getBegin() < current_sentence.getBegin() || 
                        original_concept.getEnd() >= current_sentence.getEnd() ) {
                    continue;
                }
                conceptCount++;
                Note_Nlp_TableProperties concept = new Note_Nlp_TableProperties( cas );
                concept.setBegin( original_concept.getBegin() );
                concept.setEnd( original_concept.getEnd() );
                concept.setOffset( String.valueOf( original_concept.getBegin() ) );
                concept.setLexical_variant( original_concept.getCoveredText() );
                // TODO - add section details
                // TODO - add OntologyConceptID iterator
                concept.setNote_nlp_source_concept_id( "" );
                // TODO - pull the snippet from here with the sentence start/end as the maximal spans
                concept.setSnippet( "" );
                // Default to exists
                concept.setTerm_exists( "y" );
                // Initialize temporal and modifiers to empty
                concept.setTerm_temporal( "" );
                concept.setTerm_modifiers( "" );
                // TODO - pass NLP System details through
                //concept.setNlp_system( mNlpSystem );
                concepts.add(concept);
                concept.addToIndexes();
            }
            //analyze context for concepts in the current sentence
            _contextAnalyzer.applyContext( concepts ,
                    current_sentence.getCoveredText() , 
                    currentSectionId );

//            for( Note_Nlp_TableProperties new_concept : concepts ){
//              // TODO - add confusion matrix incrementing here      
//            }

            //move to next sentence
            concepts.clear();
        }
        //mLogger.info( "\tSentences: " + String.valueOf( sentenceCount ) );
        //mLogger.info( "\tConcepts:  " + String.valueOf( conceptCount ) );
    }

    @Override
    public void process(JCas cas) throws AnalysisEngineProcessException
    {
        try{
            // Go through all the sections
            Collection<NoteSection> sections = JCasUtil.select( cas , NoteSection.class );
            //                      mLogger.info( "Sections found: " + String.valueOf( sections.size() ) );
            int lastSectionBegin = -1;
            String lastSectionId = "Unknown/Unclassified";
            for( NoteSection current_section : sections ){
                // TODO - verify that JCasUtil.select is guaranteed to return in sorted order
                if( lastSectionBegin == -1 ){
                    lastSectionBegin = current_section.getBegin();
                    continue;
                }
                processSection( cas , 
                        lastSectionId , 
                        lastSectionBegin , 
                        current_section.getBegin() );
                lastSectionBegin = current_section.getBegin();
                lastSectionId = current_section.getSectionId();
            }
            // If we didn't find any sections, then process as if the last section
            // started at character offset 0, thus including all concepts 
            // in "the current" section.
            if( sections.size() == 0 ){
                lastSectionBegin = 0;
            }
            processSection( cas , 
                    lastSectionId , 
                    lastSectionBegin , 
                    -1 );
        } catch(Exception e){
            throw new AnalysisEngineProcessException(e);
        }
    }

    public void destroy() {
        if( mClassMap != null ){
            int tp = 0;
            int fp = 0;
            int tn = 0;
            int fn = 0;
            String header = "";
            for( int pred = 0; pred < mClassHashMapIndex2Str.size(); pred++ ){
                String strPred = mClassHashMapIndex2Str.get( Integer.toString( pred + 1 ) );
                header += "\t" + strPred;
            }
            mLogger.debug( header );
            System.err.println( "*** Results" );
            System.err.println( header );
            for( int ref = 0; ref < mClassHashMapIndex2Str.size(); ref++ ){
                String strRef = mClassHashMapIndex2Str.get( Integer.toString( ref + 1 ) );
                String row = strRef;
                for( int pred = 0; pred < mClassHashMapIndex2Str.size(); pred++ ){
                    String strPred = mClassHashMapIndex2Str.get( Integer.toString( pred + 1 ) );
                    int count = mConfusionMatrix[ ref ][ pred ];
                    row += "\t" + Integer.toString( count );
                    if( strRef.equals( strPred ) ){
                        tp += count;
                    } else {
                        fp += count;
                    }
                }
                mLogger.debug( row );
                System.err.println( row );
            }
            double accuracy = Double.valueOf( tp ) / ( Double.valueOf( tp + fp ) );
            mLogger.info( "Match = " + Integer.toString( tp ) + 
                    " Mismatch = " + Integer.toString( fp ) +
                    " Accuracy = " + String.format( "%.2f" , accuracy ) );
            System.err.println( "Match = " + Integer.toString( tp ) + 
                    " Mismatch = " + Integer.toString( fp ) +
                    " Accuracy = " + String.format( "%.2f" , accuracy ) );
        }
    }

}
