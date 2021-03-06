package edu.musc.tbic.context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.ProcessTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;

import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;

import edu.musc.tbic.omop_cdm.Note_Nlp_TableProperties;
import edu.musc.tbic.omop_cdm.Note_TableProperties;
import edu.musc.tbic.uima.FeatureGen;
import org.apache.ctakes.typesystem.type.textspan.Sentence;
import edu.utah.bmi.nlp.core.SimpleParser;
import edu.utah.bmi.nlp.core.Span;
import edu.utah.bmi.nlp.fastcontext.FastContext;

public class AlAttrFeatureGen extends JCasAnnotator_ImplBase {

    private static final Logger mLogger = LoggerFactory.getLogger( FeatureGen.class );
    
    public static final String PARAM_OUTPUTDIR = "OutputDirectory";
    public static final String PARAM_FVFILE = "FvFile";
    public static final String PARAM_BOWMAP = "BowMap";
    
    String outputDirectoryName;
    String fvFileName;  
    
    public static final String PARAM_MODELFILE = "ModelFile";
    public static final String PARAM_CLASSMAP = "ClassMap";

    Path mModelFile;
    static String mClassMap;
    String mBowMapFilename;
    TreeMap<String, Integer> mBowMap;
    HashSet<String> mFeatureSet;

    static HashMap<String, String> mClassHashMapIndex2Str;
    static HashMap<String, String> mClassHashMapStr2Index;

    Model mModel;

    ArrayList<HashMap<String, String>> fvs;
    TreeSet<String> prefix;

    File iv_outputDirectory;

    FastContext fcEngine;

    int sub = 3;

    long ctxTime = 0;
    
    int [][] mConfusionMatrix;

    public void initialize( UimaContext context ) throws ResourceInitializationException 
    {

        outputDirectoryName = (String) context.getConfigParameterValue(PARAM_OUTPUTDIR);
        fvFileName = (String) context.getConfigParameterValue(PARAM_FVFILE);

        iv_outputDirectory = new File(outputDirectoryName);
        if( !iv_outputDirectory.exists() ){
            iv_outputDirectory.mkdirs();
        } else if( !iv_outputDirectory.isDirectory() ){
            throw new ResourceInitializationException(
                    new Exception("Parameter setting 'OutputDirectory' does not point to an existing directory."));
        }   
        
        fvs = new ArrayList<HashMap<String, String>>();
        // Original source:
        //   - https://github.com/jianlins/FastContext/blob/master/conf/context.txt
        fcEngine = new FastContext( "../fastcontext_uima_2.9.0/resources/context.txt", false);

        mModel = null;
        mBowMap = new TreeMap<String, Integer>();
        mClassHashMapIndex2Str = new HashMap<>();
        mClassHashMapStr2Index = new HashMap<>();
        if( ! ( (String) context.getConfigParameterValue(PARAM_MODELFILE) ).equals( "" ) ){
            String modelFilename = (String) context.getConfigParameterValue(PARAM_MODELFILE);
            mModelFile = Paths.get( modelFilename );
            mClassMap = (String) context.getConfigParameterValue(PARAM_CLASSMAP);
            mBowMapFilename = (String) context.getConfigParameterValue(PARAM_BOWMAP);
                        
            try {
                mModel = Model.load( mModelFile );
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            
            // TODO - load this from file
            mFeatureSet = new HashSet<String>();
            mFeatureSet.add( "w" );
            mFeatureSet.add( "wB" );
            mFeatureSet.add( "pw3" );
            mFeatureSet.add( "nw3" );
            mFeatureSet.add( "pw7" );
            mFeatureSet.add( "nw7" );
            mFeatureSet.add( "pwB3" );
            mFeatureSet.add( "nwB3" );
            mFeatureSet.add( "pwS" );
            mFeatureSet.add( "nwS" );
            mFeatureSet.add( "p" );
            mFeatureSet.add( "pp3" );
            mFeatureSet.add( "np3" );
            mFeatureSet.add( "s" );
            // FastContext extracted features
            mFeatureSet.add( "neg" );
            mFeatureSet.add( "hyp" );
            mFeatureSet.add( "exp" );
            mFeatureSet.add( "hist" );
            
            readClassMapFile();
            readBowMap( mBowMapFilename , mBowMap );
            
            mConfusionMatrix = new int[ mClassHashMapIndex2Str.size() + 1 ][ mClassHashMapIndex2Str.size() + 1 ];
        }
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

    public static void readBowMap( String file , TreeMap<String, Integer> bowMap ) {

        String str = "";
        {
            BufferedReader txtin = null;
            try {

                txtin = new BufferedReader(new FileReader(file));
                while ((str = txtin.readLine()) != null) {
                    String strA[] = str.split("\\|\\|");
                    bowMap.put(strA[0], Integer.parseInt(strA[1]));
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
        mLogger.debug( "BoW Size: " + bowMap.size() );

    }

    private void setSentMap(JCas jCas, HashMap<Integer, Integer> map, FSIterator<?> sentences, 
            ArrayList<Sentence> sents, ArrayList<String> sentStrs) throws ResourceProcessException {
        int sNum = 0;
        while (sentences.hasNext()) {
            Sentence s = (Sentence) sentences.next();
            sNum = s.getSentenceNumber() + 1;
            int b = s.getBegin();
            int e = s.getEnd();

            for (int i = b; i < e; i++) {
                map.put(i, sNum);
            }
            sents.add(s);

            String sStr = "";
            // Go through all the concepts using select since subiterator doesn't play nicely with uimaFIT
            // cf. https://issues.apache.org/jira/browse/CTAKES-16
            Collection<BaseToken> tokens = JCasUtil.select( jCas , BaseToken.class );
            for( BaseToken t : tokens ){
                if( b <= t.getBegin() &&
                    t.getEnd() <= e ) {
                    sStr += t.getCoveredText() + " ";
                }
            }
            
            sentStrs.add(sStr.trim());
        }
        mLogger.debug( "Sentences found: " + Integer.toString( sNum ) );
    }

    private void setTokMap(HashMap<Integer, Integer> map, 
            ArrayList<BaseToken> tokList, FSIterator<?> tokens) throws ResourceProcessException {
        int tId = 0;
        while (tokens.hasNext()) {
            BaseToken s = (BaseToken) tokens.next();
            int b = s.getBegin();
            int e = s.getEnd();

            for (int i = b; i < e; i++) {
                map.put(i, tId);
            }
            tokList.add(s);
            tId++;
        }
    }

    private String getLists(JCas jCas, Annotation c, String f) {
        String str = "";
        FSIterator<?> tokens = jCas.getAnnotationIndex(BaseToken.type).subiterator(c);
        int i = 0;
        while (tokens.hasNext()) {
            BaseToken t = (BaseToken) tokens.next();

            String s;
            if (f.equals("w")) {
                s = t.getCoveredText();
            } else if (f.equals("p")) {
                s = t.getPartOfSpeech();
            } else if (f.equals("s")) {
                s = t.getCoveredText();
                if (s.length() >= sub) {
                    return s.substring(0, sub);
                }
            } else {
                s = t.getCoveredText();
            }

            if (i == 0) {
                str = s;
            } else {
                str += "||" + s;
            }
            i++;
        }

        if (str.isEmpty()) {
            if (f.equals("w")) {
                str = c.getCoveredText();
            } else if (f.equals("p")) {
                str = "<emp>";
            } else if (f.equals("s")) {
                str = c.getCoveredText();
                if (str.length() >= sub) {
                    return str.substring(0, sub);
                }
            } else {
                str = c.getCoveredText();
            }
        }
        if (str.trim().isEmpty()) {
            str = "<emp>";
        }
        return str;
    }

    private String getListsB(JCas jCas, Annotation c, String f) {
        String str = "";
        FSIterator<?> tokens = jCas.getAnnotationIndex(BaseToken.type).subiterator(c);		
        int i = 0;
        String pre = "";
        while (tokens.hasNext()) {
            BaseToken t = (BaseToken) tokens.next();
            String s;
            if (f.equals("w")) {
                s = t.getCoveredText();
            } else if (f.equals("p")) {
                s = t.getPartOfSpeech();
            } else if (f.equals("s")) {
                s = t.getCoveredText();
                if (s.length() >= sub) {
                    return s.substring(0, sub);
                }
            } else {
                s = t.getCoveredText();
            }
            if (i == 1) {
                str = pre + " " + s;
            } else if (i > 1) {
                str += "||" + pre + " " + s;
            }
            i++;
            pre = s; 
        }

        if (str.isEmpty()) {
            if (f.equals("w")) {
                str = c.getCoveredText();
            } else if (f.equals("p")) {
                str = "<emp>";
            } else if (f.equals("s")) {
                str = c.getCoveredText();
                if (str.length() >= sub) {
                    return str.substring(0, sub);
                }
            } else {
                str = c.getCoveredText();
            }
        }
        if (str.trim().isEmpty()) {
            str = "<emp>";
        }
        return str;
    }	

    private String getListsP(JCas jCas, ArrayList<BaseToken> tokList, int idx, int nB, String f) {
        String str = "";
        int iS = Math.max(0, idx - nB);
        for (int i = iS; i < idx; i++) {
            String s;
            if (f.equals("w")) {
                s = tokList.get(i).getCoveredText();
            } else if (f.equals("p")) {
                s = tokList.get(i).getPartOfSpeech();
            } else if (f.equals("s")) {
                s = tokList.get(i).getCoveredText();
                if (s.length() >= sub) {
                    return s.substring(0, sub);
                }
            } else {
                s = tokList.get(i).getCoveredText();
            }

            if (i == iS) {
                str += s;				
            } else {
                str += "||" + s;
            }
        }

        if (str.trim().isEmpty()) {
            str = "<emp>";
        }
        return str;
    }

    private String getListsPB(JCas jCas, ArrayList<BaseToken> tokList, int idx, int nB, String f) {
        String str = "";
        int iS = Math.max(0, idx - nB);
        String pre = "";
        for (int i = iS; i < idx; i++) {
            String s;
            if (f.equals("w")) {
                s = tokList.get(i).getCoveredText();
            } else if (f.equals("p")) {
                s = tokList.get(i).getPartOfSpeech();
            } else if (f.equals("s")) {
                s = tokList.get(i).getCoveredText();
                if (s.length() >= sub) {
                    return s.substring(0, sub);
                }
            } else {
                s = tokList.get(i).getCoveredText();
            }

            if (i == iS + 1) {
                str = pre + " " + s;
            } else if ( i > iS + 1){
                str += "||" + pre + " " + s;
            }
            pre = s;
        }

        if (str.trim().isEmpty()) {
            str = "<emp>";
        }
        return str;
    }

    private String getPwordsS(JCas jCas, ArrayList<BaseToken> tokList, int idx, 
            HashMap<Integer, Integer> sentMap, int sI) {
        String str = "";

        for (int i = idx - 1; i >= 0; i--) {

            int sent = sentMap.get(tokList.get(i).getBegin());
            if (sent != sI) {
                break;
            }
            if (i == idx - 1) {
                str = tokList.get(i).getCoveredText();
            } else {
                str = tokList.get(i).getCoveredText() + "||" + str;
            }
        }

        if (str.trim().isEmpty()) {
            str = "<emp>";
        }
        return str;
    }

    private String getListsN(JCas jCas, ArrayList<BaseToken> tokList, int idx, int nB, String f) {
        String str = "";
        int iE = Math.min(tokList.size() - 1, idx + nB);
        for (int i = idx + 1; i <= iE; i++) {
            String s;
            if (f.equals("w")) {
                s = tokList.get(i).getCoveredText();
            } else if (f.equals("p")) {
                s = tokList.get(i).getPartOfSpeech();
            } else if (f.equals("s")) {
                s = tokList.get(i).getCoveredText();
                if (s.length() >= sub) {
                    return s.substring(0, sub);
                }
            } else {
                s = tokList.get(i).getCoveredText();
            }

            if (i == idx + 1) {
                str = s;
            } else {
                str += "||" + s;
            }
        }

        if (str.trim().isEmpty()) {
            str = "<emp>";
        }
        return str;
    }

    private String getListsNB(JCas jCas, ArrayList<BaseToken> tokList, int idx, int nB, String f) {
        String str = "";
        int iE = Math.min(tokList.size() - 1, idx + nB);
        String pre = "";
        for (int i = idx + 1; i <= iE; i++) {
            String s;
            if (f.equals("w")) {
                s = tokList.get(i).getCoveredText();
            } else if (f.equals("p")) {
                s = tokList.get(i).getPartOfSpeech();
            } else if (f.equals("s")) {
                s = tokList.get(i).getCoveredText();
                if (s.length() >= sub) {
                    return s.substring(0, sub);
                }
            } else {
                s = tokList.get(i).getCoveredText();
            }

            if (i == idx + 2) {
                str = pre + " " + s;
            } else if ( i > idx + 2) {
                str += "||" + pre + " " + s;
            }
            pre = s;
        }

        if (str.trim().isEmpty()) {
            str = "<emp>";
        }
        return str;
    }

    private String getNwordsS(JCas jCas, ArrayList<BaseToken> tokList, int idx, 
            HashMap<Integer, Integer> sentMap, int sI) {

        String str = "";
        for (int i = idx + 1; i < tokList.size(); i++) {
            int sent = sentMap.get(tokList.get(i).getBegin());
            if (sent != sI) {
                break;
            }

            if (i == idx + 1) {
                str = tokList.get(i).getCoveredText();
            } else {
                str += "||" + tokList.get(i).getCoveredText();
            }
        }

        if (str.trim().isEmpty()) {
            str = "<emp>";
        }
        return str;
    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        String note_id = "";
        FSIndex<Note_TableProperties> note_props_index = aJCas.getAnnotationIndex( Note_TableProperties.type );
        Iterator<Note_TableProperties> note_props_iter = note_props_index.iterator();   
        if( note_props_iter.hasNext() ) {
            Note_TableProperties note_props = (Note_TableProperties)note_props_iter.next();
            note_id = note_props.getNote_id();
        } else {
            FSIterator<?> it = aJCas.getAnnotationIndex(SourceDocumentInformation.type).iterator();
            if( it.hasNext() ){
                SourceDocumentInformation fileLoc = (SourceDocumentInformation) it.next();
                note_id = fileLoc.getUri().toString();
            }
            if( note_id.endsWith( ".txt" ) ){
                note_id = note_id.substring( 0 , note_id.length() - 4 );
            }
        }

        HashMap<Integer, Integer> sentMap = new HashMap<>(); 
        ArrayList<Sentence> sents = new ArrayList<>();
        ArrayList<String> sentStrs = new ArrayList<>();
        FSIterator<?> sentences = aJCas.getAnnotationIndex(Sentence.type).iterator();
        try {
            setSentMap(aJCas, sentMap, sentences, sents, sentStrs);
        } catch (ResourceProcessException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        HashMap<Integer, Integer> tokMap = new HashMap<>(); 
        ArrayList<BaseToken> tokList = new ArrayList<>();
        FSIterator<?> tokens = aJCas.getAnnotationIndex(BaseToken.type).iterator();
        try {
            setTokMap(tokMap, tokList, tokens);
        } catch (ResourceProcessException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        ArrayList<IdentifiedAnnotation> conList = new ArrayList<>();
        // TODO - allow concept types to focus on to be configured via parameter
        FSIterator<?> cons = aJCas.getAnnotationIndex(IdentifiedAnnotation.type).iterator();
        while (cons.hasNext()) {
            IdentifiedAnnotation c = (IdentifiedAnnotation) cons.next();
            // for i2b2 2010 
            // TODO
            //		    if (c.getType().equalsIgnoreCase("treatment") || c.getType().equalsIgnoreCase("test")) {
            //			continue;
            //		    }
            conList.add(c);
        }
	
        String noteBody = aJCas.getDocumentText();
        for (int i = 0; i < conList.size(); i++) {
            IdentifiedAnnotation c = conList.get(i);

            int b = c.getBegin();
            int e = c.getEnd();

            String label = note_id + " " + b + " " + e;
            // TODO - dig up original edu.musc.ce.type.Concept type and figure out how attr is defined
            String attr = "present"; // TODO - c.getAttr();
            if( c.getSubject().equalsIgnoreCase( "not patient" ) ){
                attr = "not_patient";
            } else if( c.getUncertainty() == 1 ){
                attr = "uncertain";
            } else if( c.getPolarity() == -1 ){
                attr = "negated";
            } else if( c.getHistoryOf() == 1 ){
                attr = "hypothetical";
            } else if( c.getConditional() ){
                attr = "conditional";
            }

            String w = getLists(aJCas, c, "w"); // word
            String p = getLists(aJCas, c, "p"); // pos
            String s = getLists(aJCas, c, "s"); // substring
            // l lemma

            String wB = getListsB(aJCas, c, "w");

            int ba = b; // actual begin span
            while (tokMap.get(ba) == null && ba < e) {
                ba++;
            }
            int ea = e - 1; // actual begin span
            while (tokMap.get(ea) == null && b <= ea) {
                ea--;
            }

            int sI = sentMap.get(ba); 

            String pw3 = getListsP(aJCas, tokList, tokMap.get(ba), 3, "w");
            String nw3 = getListsN(aJCas, tokList, tokMap.get(ea), 3, "w");
            String pw7 = getListsP(aJCas, tokList, tokMap.get(ba), 7, "w");
            String nw7 = getListsN(aJCas, tokList, tokMap.get(ea), 7, "w");

            String pp3 = getListsP(aJCas, tokList, tokMap.get(ba), 3, "p");
            String np3 = getListsN(aJCas, tokList, tokMap.get(ea), 3, "p");

            String pwB3 = getListsPB(aJCas, tokList, tokMap.get(ba), 3, "w");
            String nwB3 = getListsNB(aJCas, tokList, tokMap.get(ea), 3, "w");

            String pwS = getPwordsS(aJCas, tokList, tokMap.get(ba), sentMap, sI);
            String nwS = getNwordsS(aJCas, tokList, tokMap.get(ea), sentMap, sI);

            /* */
            String sentenceText = sentStrs.get(sI - 1);
            int cSI = sI;
            while (ea > sents.get(cSI - 1).getEnd()) {
                sentenceText += " " + sentStrs.get(cSI++);
                mLogger.warn( "The token end (ea=" + Integer.toString( ea ) + 
                        ") exceeds the sentence end (" + Integer.toString( sents.get(cSI - 1).getEnd() ) + ")" );
            }

            // TODO - this is a quick fix that needs to be made more robust with
            // an explicit log message explaining why the gap exists
            Integer sentenceBeginOffset = sents.get( sI - 1 ).getBegin();
//            while( sentenceBeginOffset > 0 &&
//                    ! tokMap.containsKey( sentenceBeginOffset ) ){
//                sentenceBeginOffset--;
//            }
            Integer sentenceEndOffset = Math.max( sents.get( sI - 1 ).getEnd() ,
                                                  c.getEnd() );
            ArrayList<Span> sentenceTokenSpans = SimpleParser.tokenizeOnWhitespaces( sentenceText );
            HashMap<Integer, Integer> offset2TokenMap = new HashMap<>();
            int tokenId = 0;
            for( Span tokenSpan : sentenceTokenSpans ){
                for( int tokenIndex = tokenSpan.getBegin() ;
                        tokenIndex <= tokenSpan.getEnd() ; 
                        tokenIndex++ ){
                    offset2TokenMap.put( tokenIndex ,  tokenId );
                }
                tokenId++;
            }
            int conceptRelativeBegin = c.getBegin() - sentenceBeginOffset;
            int conceptRelativeEnd = c.getEnd() - sentenceBeginOffset;
//            mLogger.debug( conceptRelativeBegin + " - " + conceptRelativeEnd );
            int conceptTokenBegin = -1;
            int conceptTokenEnd = -1;
            try{
                while( conceptRelativeBegin > 0 &&
                       ! offset2TokenMap.containsKey( conceptRelativeBegin ) ){
                    conceptRelativeBegin--;
                }
                if( conceptRelativeBegin == 0 ){
                    conceptTokenBegin = 0;
                } else {
                    conceptTokenBegin = offset2TokenMap.get( conceptRelativeBegin );
                }
                while( conceptRelativeEnd < sentenceText.length() &&
                       ! offset2TokenMap.containsKey( conceptRelativeEnd ) ){
                    conceptRelativeEnd++;
                }
                if( conceptRelativeEnd >= sentenceText.length() ){
                    conceptTokenEnd = sentenceTokenSpans.size() - 1;
                } else {
                    conceptTokenEnd = offset2TokenMap.get( conceptRelativeEnd );
                }
            } catch(NullPointerException er){
                mLogger.debug( "\t|" + sentStrs.get(sI - 2) +
                        "\n\t|" + sentenceText +
                        "\n\t|" + sentStrs.get(sI - 1) +
                        "\n\t|" + sentStrs.get(sI - 0) +
                        "\n\t" + sentenceTokenSpans +
                        "\n\t|" + c.getCoveredText() + "|" + 
                        String.valueOf( conceptRelativeBegin ) + " - " +
                        String.valueOf( conceptRelativeEnd ) + " | " +
                        String.valueOf( offset2TokenMap.get( conceptRelativeBegin ) ) +
                        " - " +
                        String.valueOf( offset2TokenMap.get( conceptRelativeEnd ) ) );
            }
//            while( sentenceBeginOffset > 0 &&
//                    ! tokMap.containsKey( sentenceBeginOffset ) ){
//                sentenceBeginOffset--;
//            }
//            int oTn = tokMap.get( sentenceBeginOffset );
//            int cTs = tokMap.get(ba) - oTn;
//            int cTe = tokMap.get(ea) - oTn;
//
//            if (cTs < 0) {
//                System.out.println(cTs);
//                cTs = 0;
//            }
//            if (cTe < 0) {
//                System.out.println(cTe);
//                cTe = 0;
//            }

            HashMap<String, String> f = new HashMap<>();
            f.put("label", label);

            f.put("attr", attr);
	    
            // 
            f.put( "concept" , c.getCoveredText() );
//            mLogger.debug( "c = " + c.getCoveredText() + " [ " + 
//                    Integer.toString( c.getBegin() ) + " , " + 
//                    Integer.toString( c.getEnd() ) + " ] " +
//                    Integer.toString( sentenceEndOffset ) + " " +
//                    Integer.toString( noteBody.length() ) );
            f.put( "sentPrefix" , noteBody.substring( Math.max( 0 , 
                                                                sentenceBeginOffset ) , 
                                                      c.getBegin() ) );
            f.put( "sentSuffix" , noteBody.substring( c.getEnd() , 
                                                      Math.min( noteBody.length() , sentenceEndOffset ) ) );
            
            //words
            f.put("w",  w);
            f.put("wB",  wB);
            //previous words 
            f.put("pw3",  pw3);
            f.put("pwB3",  pwB3);
            f.put("pw7",  pw7);
            //previous words in the sentence
            f.put("pwS",  pwS);

            //next words 
            f.put("nw3",  nw3);
            f.put("nwB3",  nwB3);
            f.put("nw7",  nw7);
            //next words in the sentence
            f.put("nwS",  nwS);

            // pos
            f.put("p",  p);
            f.put("pp3",  pp3);
            f.put("np3",  np3);

            //substring
            f.put("s",  s);

//            /* */
//            long startTime = System.nanoTime();
//            setConTxt("neg", "hyp", "exp", "his", sentenceText , cTs, cTe, f);
            setConTxt( "neg" , "hyp" , "exp" , "his" , 
                    sentenceText , sentenceTokenSpans , 
                    conceptTokenBegin , conceptTokenEnd , 
                    f );
//            long endTime = System.nanoTime();
//            /* */

//            ctxTime += endTime - startTime; 

            if( mModel != null ){
                predict( aJCas , b , e , f );
            }
            
            fvs.add(f);

        }

        try {
            appendFeatureFile();
        } catch (ResourceProcessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void predict( JCas jcas , 
                         int beginOffset , int endOffset , 
                         HashMap<String, String> featureMap ){
        
        Note_Nlp_TableProperties noteNlpConcept = new Note_Nlp_TableProperties( jcas ,
                beginOffset ,
                endOffset );
        
        ArrayList<Feature> conceptFeatureList = new ArrayList<Feature>();
        for( HashMap.Entry<String, String> thisFeature : featureMap.entrySet() ){
            if( !mFeatureSet.contains( thisFeature.getKey() ) ){
                continue;
            }
            String featureValuePair = thisFeature.getKey() + "_" + thisFeature.getValue();
            if( mBowMap.containsKey( featureValuePair ) ){
                // Add 1001 for sentence meta info
                int featureKey = mBowMap.get( featureValuePair ) + 1001;
                conceptFeatureList.add( new FeatureNode( featureKey , 1 ) );
            }
        }
        
        Feature[] conceptFeatureArray = new Feature[ conceptFeatureList.size() ];
        for( int i = 0 ; i < conceptFeatureArray.length ; i++ ){
            conceptFeatureArray[ i ] = conceptFeatureList.get( i );
        }
        //        new FeatureNode(1, 4), new FeatureNode(2, 2) };
        int prediction = (int) Linear.predict( mModel , conceptFeatureArray );
        String strPrediction = mClassHashMapIndex2Str.get( Integer.toString( prediction ) );
        String referenceIndex = mClassHashMapStr2Index.get( featureMap.get( "attr" ) );
        
        mConfusionMatrix[ Integer.parseInt( referenceIndex ) - 1 ][ prediction - 1 ] += 1;

        String conditionalValue = "false";
        String genericValue = "false";
        String historicalValue = "0";
        String polarityValue = "1";
        String subjectValue = "patient";
        String uncertaintyValue = "0";
        if( strPrediction.equalsIgnoreCase( "conditional" ) ){
            conditionalValue = "true";
        } else if( strPrediction.equalsIgnoreCase( "hypothetical" ) ){
            historicalValue = "1";
        } else if( strPrediction.equalsIgnoreCase( "negated" ) ){
            polarityValue = "-1";
        } else if( strPrediction.equalsIgnoreCase( "not_patient" ) ){
            subjectValue = "not patient";
        } else if( strPrediction.equalsIgnoreCase( "uncertain" ) ){
            uncertaintyValue = "1";
        } else if( ! strPrediction.equalsIgnoreCase( "present" ) ){
            mLogger.warn( "Unrecognized model prediction " + 
                          strPrediction + 
                          " (" + Integer.toString( prediction ) + "). Treating as 'present'." );
        }
        // Update the term_exists flag to match
        if( conditionalValue.equals( "false" ) &&
            genericValue.equals( "false" ) &&
            historicalValue.equals( "0" ) &&
            polarityValue.equals( "1" ) &&
            subjectValue.equals( "patient" ) &&
            uncertaintyValue.equals( "0" ) ){
            noteNlpConcept.setTerm_exists( "y" );
        } else {
            noteNlpConcept.setTerm_exists( "n" );
        }
        // String all the modifier values together with a semicolon
        String termModifiers = String.join( ";" ,
                "conditional=" + conditionalValue ,
                "generic=" + genericValue ,
                "historical=" + historicalValue ,
                "polarity=" + polarityValue ,
                "subject=" + subjectValue ,
                "uncertainty=" + uncertaintyValue );
        noteNlpConcept.setTerm_modifiers( termModifiers );

        //mLogger.debug( "\t\tPrediction: " + strPrediction + " (" +
        //               Integer.toString( prediction ) + ") =?= " + featureMap.get( "attr" ) ); 
        noteNlpConcept.addToIndexes();
        
    }
    
    /*
      Negation
      Certainty
      Temporality
      Experiencer
     */
    
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

//        mLogger.debug( "FastContext Result: " + res + 
//                "\n\t" + negValue + "|" + hypValue + "|" + expValue + "|" + hisValue );
        
        contextAttributes.put( lblNegated , negValue );
        contextAttributes.put( lblHypothetical , hypValue );
        contextAttributes.put( lblExperiencer , expValue );
        contextAttributes.put( lblHistorical , hisValue );
    }

    /* */
    public void setConTxt(String negS, String hypS, String expS, String hisS, 
            String sStr, int cTs, int cTe, HashMap<String, String> f) {

        String neg = "affirm";
        String hyp = "certain";
        String exp = "patient";
        String his = "present";

        ArrayList<Span> sTs = SimpleParser.tokenizeOnWhitespaces(sStr);
        ArrayList<String> res = fcEngine.processContext(sTs, cTs, cTe, sStr, 30);

        for (String re : res) {
            //System.out.println(re);
            if (re.equalsIgnoreCase("negated")) {
                neg = "negated";
            }
            if (re.equalsIgnoreCase("nonpatient")) {
                exp = "nonpatient";
            }
            if (re.equalsIgnoreCase("uncertain")) {
                hyp = "uncertain";
            }
            if (re.equalsIgnoreCase("historical")) {
                his = "historical";
            }
            if (re.equalsIgnoreCase("hypothetical")) {
                his = "hypothetical";
            }
        }

        f.put(negS, neg);
        f.put(hypS, hyp);
        f.put(expS, exp);
        f.put(hisS, his);
    }
    /* */

    public void label(String filename) throws IOException
    {
        File listFile = new File(filename);
        PrintStream out = new PrintStream(new FileOutputStream(listFile));

        for (int i=0; i< fvs.size(); i++) { 
            HashMap<String, String> f = fvs.get(i);
            out.println(f.get("label"));
        }
        out.flush();
        out.close();
    }

    // for liblinear
    public void appendFeatureFile() throws ResourceProcessException, IOException 
    {
        label(outputDirectoryName + "/" + fvFileName + ".lbl");

        PrintStream outSvm = new PrintStream( new FileOutputStream( outputDirectoryName + "/" + fvFileName ));

        PrintStream outFastText = new PrintStream( new FileOutputStream( outputDirectoryName + "/" + 
                                                                         fvFileName + ".ftxt" ) );

        for (int i=0; i< fvs.size(); i++) { 
            StringBuffer sbSvm = new StringBuffer();
            StringBuffer sbFastText = new StringBuffer();
            
            HashMap<String, String> f = fvs.get(i);

            appendL(sbSvm, f.get("w"), "w");
            appendL(sbSvm, f.get("wB"), "wB");

            appendL(sbSvm, f.get("pw3"), "pw3");
            appendL(sbSvm, f.get("pwB3"), "pwB3");
            appendL(sbSvm, f.get("pw7"), "pw7");
            appendL(sbSvm, f.get("pwS"), "pwS");

            appendL(sbSvm, f.get("nw3"), "nw3");
            appendL(sbSvm, f.get("nwB3"), "nwB3");
            appendL(sbSvm, f.get("nw7"), "nw7");
            appendL(sbSvm, f.get("nwS"), "nwS");

            append(sbSvm, f.get("p"), "p");
            append(sbSvm, f.get("pp3"), "pp3");
            append(sbSvm, f.get("np3"), "np3");

            appendL(sbSvm, f.get("s"), "s");

            append(sbSvm, f.get("neg"), "neg");
            append(sbSvm, f.get("hyp"), "hyp");
            append(sbSvm, f.get("exp"), "exp");
            append(sbSvm, f.get("his"), "his");

            outSvm.println(f.get("attr") + "||" + sbSvm.toString());
            outFastText.println( "__label__" + f.get( "attr" ) + " " + 
                    f.get( "sentPrefix" ) + 
                    "@CONS$ " + f.get( "concept" ) + " @CONS$" + 
                    f.get( "sentSuffix" ) );
        }
        outSvm.flush();
        outSvm.close();
        outFastText.flush();
        outFastText.close();

    }

    // fastText format for fastText or ELMo embeddings
    public void collectionProcessComplete(ProcessTrace arg0) throws ResourceProcessException, IOException 
    {
        System.out.println("Context Execution time in milliseconds : " + ctxTime / 1000000);		

        label(outputDirectoryName + "/" + fvFileName + ".lbl.ft");

        PrintStream out = new PrintStream(new FileOutputStream(outputDirectoryName + "/" + fvFileName + ".ft"));

        for (int i=0; i< fvs.size(); i++) { 
            HashMap<String, String> f = fvs.get(i);

            String w = appendFN(f.get("w"));
            String pwS = appendFN(f.get("pwS"));
            String nwS = appendFN(f.get("nwS"));

            String cStr = pwS + " @CON$ " + w + " @CON$ " + nwS;
            out.println("__label__" + f.get("attr") + " , " + cStr);
        }
        out.flush();
        out.close();
    }


    public void append(StringBuffer sb, String f, String prefix) {

        if (f == null) {
            return;
        }

        String strA[] = null;

        strA = f.split("\\|\\|");
        String tmp = "";

        for(int j = 0; j < strA.length; j++) {
            if (strA[j] == null || strA[j].trim().isEmpty()) {
                continue;
            }
            tmp += prefix + "_" + strA[j] + "||";
        }

        sb.append(tmp);
    }

    public void appendL(StringBuffer sb, String f, String prefix) {

        if (f == null) {
            return;
        }

        String strA[] = null;

        strA = f.split("\\|\\|");
        String tmp = "";

        for(int j = 0; j < strA.length; j++) {
            if (strA[j] == null || strA[j].trim().isEmpty()) {
                continue;
            }
            tmp += prefix + "_" + strA[j].toLowerCase() + "||";
        }

        sb.append(tmp);
    }

    public void appendV(StringBuffer sb, String f, String prefix) {

        if (f == null) {
            return;
        }

        String strA[] = null;

        strA = f.split("\\|\\|");
        String tmp = "|" + prefix + " ";

        for(int j = 0; j < strA.length; j++) {
            if (strA[j] == null || strA[j].trim().isEmpty()) {
                continue;
            }
            String tt = strA[j].replace("|", "").replace(":", "").replaceAll("\\s+", " ").trim();
            tmp += tt + " ";
        }

        sb.append(tmp);
    }

    public void appendVL(StringBuffer sb, String f, String prefix) {

        if (f == null) {
            return;
        }

        String strA[] = null;

        strA = f.split("\\|\\|");
        String tmp = "|" + prefix + " ";

        for(int j = 0; j < strA.length; j++) {
            if (strA[j] == null || strA[j].trim().isEmpty()) {
                continue;
            }
            String tt = strA[j].replace("|", "").replace(":", "").replaceAll("\\s+", " ").trim();
            tmp += tt.toLowerCase() + " ";
        }

        sb.append(tmp);
    }

    public void appendF(StringBuffer sb, String f, String prefix) {

        if (f == null) {
            return;
        }

        if (f.equalsIgnoreCase("<emp>")) {
            return;
        }

        String strA[] = null;

        strA = f.split("\\|\\|");
        String tmp = "";

        for(int j = 0; j < strA.length; j++) {
            if (strA[j] == null || strA[j].trim().isEmpty()) {
                continue;
            }
            tmp += prefix + "_" + strA[j] + "||";
        }

        sb.append(tmp);
    }

    public String appendFN(String f) {

        if (f == null) {
            return "";
        }

        if (f.equalsIgnoreCase("<emp>")) {
            return "";
        }

        String strA[] = null;

        strA = f.split("\\|\\|");
        String tmp = "";

        for(int j = 0; j < strA.length; j++) {
            if (strA[j] == null || strA[j].trim().isEmpty()) {
                continue;
            }
            tmp += strA[j] + " ";
        }

        return tmp.trim();
    }

    public void appendE(StringBuffer sb, String f) {

        if (f == null) {
            return;
        }

        String strA[] = null;

        strA = f.split("\\|\\|");
        String tmp = "";

        for(int j = 0; j < strA.length; j++) {
            if (strA[j] == null || strA[j].trim().isEmpty()) {
                continue;
            }
            tmp += strA[j].replace("\"", "'") + " ";
        }

        if (tmp.equalsIgnoreCase("<emp> ")) {
            return;
        }

        sb.append(tmp);
    }	

    public void appendB(StringBuffer sb, String f) {

        if (f == null) {
            return;
        }

        String strA[] = null;

        strA = f.split("\\|\\|");
        String tmp = "";

        for(int j = 0; j < strA.length; j++) {
            if (strA[j] == null || strA[j].trim().isEmpty()) {
                continue;
            }
            tmp += strA[j].toLowerCase().replace("\"", "'") + " ";
        }

        if (tmp.equalsIgnoreCase("<emp> ")) {
            return;
        }

        sb.append(tmp);
    }

    public void destroy() {
        if( mModel != null ){
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
