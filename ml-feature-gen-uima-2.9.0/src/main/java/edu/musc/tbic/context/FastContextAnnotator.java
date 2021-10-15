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
import edu.utah.bmi.nlp.core.SimpleParser;
import edu.utah.bmi.nlp.core.Span;
import edu.utah.bmi.nlp.fastcontext.FastContext;

import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import edu.musc.tbic.omop_cdm.Note_Nlp_TableProperties;

public class FastContextAnnotator extends JCasAnnotator_ImplBase {

    private static final Logger mLogger = LoggerFactory.getLogger( FeatureGen.class );

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

    FastContext fcEngine;

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

        fcEngine = new FastContext( "../fastcontext_uima_2.9.0/resources/context.txt", false);

        mClassMap = null;

        mClassHashMapIndex2Str = new HashMap<>();
        mClassHashMapStr2Index = new HashMap<>();

        mClassMap = (String) aContext.getConfigParameterValue(PARAM_CLASSMAP);
        readClassMapFile();
        mConfusionMatrix = new int[ mClassHashMapIndex2Str.size() + 1 ][ mClassHashMapIndex2Str.size() + 2 ];

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

    @Override
    public void process(JCas cas) throws AnalysisEngineProcessException
    {
        try{
            // Go through all the sentences using select since subiterator doesn't play nicely with uimaFIT
            // cf. https://issues.apache.org/jira/browse/CTAKES-16
            Collection<Sentence> sentences = JCasUtil.select( cas , Sentence.class );

            ArrayList<Note_Nlp_TableProperties> concepts = new ArrayList<Note_Nlp_TableProperties>();

            int sentenceCount = 0;
            int conceptCount = 0;
            for( Sentence current_sentence : sentences ){
                sentenceCount++;
                String sentenceText = current_sentence.getCoveredText();
                ArrayList<Span> sentenceTokenSpans = SimpleParser.tokenizeOnWhitespaces( sentenceText );
                // TODO - It's only worth building this map if there is a concept in this sentence
                HashMap<Integer, Integer> offset2TokenMap = null;

                // Go through all the concepts using select since subiterator doesn't play nicely with uimaFIT
                // cf. https://issues.apache.org/jira/browse/CTAKES-16
                Collection<IdentifiedAnnotation> original_concepts = JCasUtil.select( cas , IdentifiedAnnotation.class );
                for( IdentifiedAnnotation original_concept : original_concepts ){
                    if( original_concept.getBegin() < current_sentence.getBegin() || 
                            original_concept.getEnd() >= current_sentence.getEnd() ) {
                        continue;
                    }
                    // TODO - It's only worth building this map if there is a concept in this sentence
                    if( offset2TokenMap == null ){
                        offset2TokenMap = new HashMap<>();
                        int tokenId = 0;
                        for( Span tokenSpan : sentenceTokenSpans ){
                            for( int i = tokenSpan.getBegin() ;
                                    i <= tokenSpan.getEnd() ; 
                                    i++ ){
                                offset2TokenMap.put( i ,  tokenId );
                            }
                            tokenId++;
                        }
                    }
                    int conceptRelativeBegin = original_concept.getBegin() - current_sentence.getBegin();
                    int conceptRelativeEnd = original_concept.getEnd() - current_sentence.getBegin();
                    int conceptTokenBegin = offset2TokenMap.get( conceptRelativeBegin );
                    int conceptTokenEnd = offset2TokenMap.get( conceptRelativeEnd );
                    conceptCount++;
                    Note_Nlp_TableProperties concept = new Note_Nlp_TableProperties( cas );
                    concept.setBegin( original_concept.getBegin() );
                    concept.setEnd( original_concept.getEnd() );
                    concept.setOffset( String.valueOf( original_concept.getBegin() ) );
                    concept.setLexical_variant( original_concept.getCoveredText() );
                    // TODO - add section details
                    // TODO - should we just force UmlsTerm
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
                    ////
                    HashMap<String, String> contextAttributes = new HashMap<>();
                    boolean errorFlag = false;
                    try{
                        setConTxt( "neg" , "hyp" , "exp" , "his" , 
                                sentenceText , sentenceTokenSpans , 
                                conceptTokenBegin , conceptTokenEnd , 
                                contextAttributes );
                    } catch( Exception e ){
                        errorFlag = true;
                        mLogger.warn( "NullPointerException. Treating as default: " + 
                                       conceptTokenBegin + " - " + conceptTokenEnd );
                    }
                    ////
                    String conditionalValue = "false";
                    String genericValue = "false";
                    String historicalValue = "0";
                    String polarityValue = "1";
                    String subjectValue = "patient";
                    String uncertaintyValue = "0";
                    // @FEATURE_VALUES|Negation|affirm|negated
                    // @FEATURE_VALUES|Certainty|certain|uncertain
                    // @FEATURE_VALUES|Temporality|present|historical|hypothetical
                    // @FEATURE_VALUES|Experiencer|patient|nonpatient
                    // No feature values impact "conditional" or "generic"
                    // - conditionalValue = "true"
                    // - genericValue = "true"
                    String predAttr = "present";
                    if( contextAttributes.get( "exp" ).equalsIgnoreCase( "nonpatient" ) ){
                        subjectValue = "not patient";
                        predAttr = "not_patient";
                    } else if( contextAttributes.get( "hyp" ).equalsIgnoreCase( "uncertain" ) ){
                        uncertaintyValue = "1";
                        predAttr = "uncertain";
                    } else if( contextAttributes.get( "neg" ).equalsIgnoreCase( "negated" ) ){
                        polarityValue = "-1";
                        predAttr = "negated";
                    } else if( contextAttributes.get( "his" ).equalsIgnoreCase( "hypothetical" ) ){
                        historicalValue = "1";
                        predAttr = "hypothetical";
                    } else if( contextAttributes.get( "his" ).equalsIgnoreCase( "historical" ) ){
                        historicalValue = "-1";
                    }
                    //
                    if( mClassMap != null ){
                        String refAttr = "present"; // TODO - c.getAttr();
                        if( original_concept.getSubject().equalsIgnoreCase( "not patient" ) ){
                            refAttr = "not_patient";
                        } else if( original_concept.getUncertainty() == 1 ){
                            refAttr = "uncertain";
                        } else if( original_concept.getPolarity() == -1 ){
                            refAttr = "negated";
                        } else if( original_concept.getHistoryOf() == 1 ){
                            refAttr = "hypothetical";
                        } else if( original_concept.getConditional() ){
                            refAttr = "conditional";
                        }
                        String predictionIndex = mClassHashMapStr2Index.get( predAttr );
                        String referenceIndex = mClassHashMapStr2Index.get( refAttr );
                        if( errorFlag ){
                            predictionIndex = Integer.toString( mClassHashMapIndex2Str.size() + 1 );
                        }
                        mConfusionMatrix[ Integer.parseInt( referenceIndex ) - 1 ][ Integer.parseInt( predictionIndex ) - 1 ] += 1;
                    }
                    // Update the term_exists flag to match
                    if( conditionalValue.equals( "false" ) &&
                            genericValue.equals( "false" ) &&
                            historicalValue.equals( "0" ) &&
                            polarityValue.equals( "1" ) &&
                            subjectValue.equals( "patient" ) &&
                            uncertaintyValue.equals( "0" ) ){
                        concept.setTerm_exists( "y" );
                    } else {
                        concept.setTerm_exists( "n" );
                    }
                    // String all the modifier values together with a semicolon
                    String termModifiers = String.join( ";" ,
                            "conditional=" + conditionalValue ,
                            "generic=" + genericValue ,
                            "historical=" + historicalValue ,
                            "polarity=" + polarityValue ,
                            "subject=" + subjectValue ,
                            "uncertainty=" + uncertaintyValue );
                    concept.setTerm_modifiers( termModifiers );
                    ////
                    concept.addToIndexes();
                }
            }
        } catch(Exception e){
            throw new AnalysisEngineProcessException(e);
        }
    }

    /* */
    public void setConTxt( String lblNegated , String lblHypothetical , 
            String lblExperiencer , String lblHistorical , 
            String sentenceText , ArrayList<Span> sentenceTokenSpans , 
            int conceptTokenBegin , int conceptTokenEnd , 
            HashMap<String, String> contextAttributes ) {

        String negValue = "affirm";
        String hypValue = "certain";
        String expValue = "patient";
        String hisValue = "present";
        // Initialize everything to its default value in case something goes wrong
        contextAttributes.put( lblNegated , negValue );
        contextAttributes.put( lblHypothetical , hypValue );
        contextAttributes.put( lblExperiencer , expValue );
        contextAttributes.put( lblHistorical , hisValue );
        
        ArrayList<String> res = fcEngine.processContext( sentenceTokenSpans , 
                conceptTokenBegin , conceptTokenEnd , 
                sentenceText , 
                30 );

        for (String re : res) {
            if (re.equalsIgnoreCase("negated")) {
                negValue = "negated";
            }
            if (re.equalsIgnoreCase("nonpatient")) {
                expValue = "nonpatient";
            }
            if (re.equalsIgnoreCase("uncertain")) {
                hypValue = "uncertain";
            }
            if (re.equalsIgnoreCase("historical")) {
                hisValue = "historical";
            }
            if (re.equalsIgnoreCase("hypothetical")) {
                hisValue = "hypothetical";
            }
        }

        contextAttributes.put( lblNegated , negValue );
        contextAttributes.put( lblHypothetical , hypValue );
        contextAttributes.put( lblExperiencer , expValue );
        contextAttributes.put( lblHistorical , hisValue );
    }

    public void destroy() {
        if( mClassMap != null ){
            int tp = 0;
            int fp = 0;
            int tn = 0;
            int fn = 0;
            int errorCount = 0;
            String header = "";
            for( int pred = 0; pred < mClassHashMapIndex2Str.size(); pred++ ){
                String strPred = mClassHashMapIndex2Str.get( Integer.toString( pred + 1 ) );
                header += "\t" + strPred;
            }
            header += "\terror";
            mLogger.debug( header );
            System.err.println( "*** Results" );
            System.err.println( header );
            for( int ref = 0; ref < mClassHashMapIndex2Str.size(); ref++ ){
                String strRef = mClassHashMapIndex2Str.get( Integer.toString( ref + 1 ) );
                String row = strRef;
                for( int pred = 0; pred <= mClassHashMapIndex2Str.size(); pred++ ){
                    String strPred = "error";
                    if( pred < mClassHashMapIndex2Str.size() ){
                        strPred = mClassHashMapIndex2Str.get( Integer.toString( pred + 1 ) );
                    }
                    int count = mConfusionMatrix[ ref ][ pred ];
                    row += "\t" + Integer.toString( count );
                    if( strRef.equals( strPred ) ){
                        tp += count;
                    } else if( strPred.equals( "error" ) ){
                        errorCount += count;
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
                    " Accuracy = " + String.format( "%.2f" , accuracy ) + 
                    " Errors = " + Integer.toString( errorCount ) );
            System.err.println( "Match = " + Integer.toString( tp ) + 
                    " Mismatch = " + Integer.toString( fp ) +
                    " Accuracy = " + String.format( "%.2f" , accuracy ) + 
                    " Errors = " + Integer.toString( errorCount ) );
        }
    }

}
