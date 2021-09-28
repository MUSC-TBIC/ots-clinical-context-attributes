package edu.musc.tbic.context;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeSet;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIndex;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.examples.SourceDocumentInformation;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.ProcessTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.ctakes.typesystem.type.syntax.BaseToken;
import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;
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

    String outputDirectoryName;
    String fvFileName;	

    ArrayList<HashMap<String, String>> fvs;
    TreeSet<String> prefix;

    File iv_outputDirectory;

    FastContext fc;

    int sub = 3;

    long ctxTime = 0;

    public void initialize( UimaContext context ) throws ResourceInitializationException 
    {

        outputDirectoryName = (String) context.getConfigParameterValue(PARAM_OUTPUTDIR);
        fvFileName = (String) context.getConfigParameterValue(PARAM_FVFILE);

        iv_outputDirectory = new File(outputDirectoryName);
        if(!iv_outputDirectory.exists() || !iv_outputDirectory.isDirectory())
            throw new ResourceInitializationException(
                    new Exception("Parameter setting 'OutputDirectory' does not point to an existing directory."));

//        try {
//            PrintStream out = new PrintStream(new FileOutputStream(outputDirectoryName + "/" + fvFileName));
//        } catch (FileNotFoundException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }

        fvs = new ArrayList<HashMap<String, String>>();
        //	// Original source:
        //	//   - https://github.com/jianlins/FastContext/blob/master/conf/context.txt
        //        //fc = new FastContext("/Users/jun/Documents/work/project/FastContext/context.txt", false);
        //        fc = new FastContext( "../fastcontext_uima_2.9.0/resources/context.txt", false);
    }

    private void setSentMap(JCas jCas, HashMap<Integer, Integer> map, FSIterator<?> sentences, 
            ArrayList<Sentence> sents, ArrayList<String> sentStrs) throws ResourceProcessException	{
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
            FSIterator<?> tokens = jCas.getAnnotationIndex(BaseToken.type).subiterator(s);		
            while (tokens.hasNext()) {
                BaseToken t = (BaseToken) tokens.next();
                sStr += t.getCoveredText() + " ";
            }
            sentStrs.add(sStr.trim());
        }
        mLogger.debug( "Sentences found: " + Integer.toString( sNum ) );
    }

    private void setTokMap(HashMap<Integer, Integer> map, 
            ArrayList<BaseToken> tokList, FSIterator<?> tokens) throws ResourceProcessException	{
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

        for (int i = 0; i < conList.size(); i++) {
            IdentifiedAnnotation c = conList.get(i);

            int b = c.getBegin();
            int e = c.getEnd();

            String label = note_id + " " + b + " " + e;
            // TODO - dig up original edu.musc.ce.type.Concept type and figure out how attr is defined
            String attr = "present"; // TODO - c.getAttr();
            if( c.getSubject().equalsIgnoreCase( "not patient" ) ){
                attr = "not_patient";
            } else if( c.getPolarity() == -1 ){
                attr = "negated";
            } else if( c.getUncertainty() == 1 ){
                attr = "uncertain";
            } else if( c.getConditional() ){
                attr = "conditional";
            } else if( c.getHistoryOf() == 1 ){
                attr = "hypothetical";
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
            String sStr = sentStrs.get(sI - 1);
            int cSI = sI;
            while (ea > sents.get(cSI - 1).getEnd()) {
                sStr += " " + sentStrs.get(cSI++);
                mLogger.warn( "The token end (ea=" + Integer.toString( ea ) + 
                        ") exceeds the sentence end (" + Integer.toString( sents.get(cSI - 1).getEnd() ) + ")" );
            }

            // TODO - this is a quick fix that needs to be made more robust with
            // an explicit log message explaining why the gap exists
            Integer sentenceBeginOffset = sents.get( sI - 1 ).getBegin();
            while( sentenceBeginOffset > 0 &&
                    ! tokMap.containsKey( sentenceBeginOffset ) ){
                sentenceBeginOffset--;
            }
            int oTn = tokMap.get( sentenceBeginOffset );
            int cTs = tokMap.get(ba) - oTn;
            int cTe = tokMap.get(ea) - oTn;

            if (cTs < 0) {
                System.out.println(cTs);
                cTs = 0;
            }
            if (cTe < 0) {
                System.out.println(cTe);
                cTe = 0;
            }

            HashMap<String, String> f = new HashMap<>();
            f.put("label", label);

            f.put("attr", attr);						

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
//            setConTxt("neg", "hyp", "exp", "his", sStr, cTs, cTe, f);
//            long endTime = System.nanoTime();
//            /* */

//            ctxTime += endTime - startTime; 

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

    /*
      Negation
      Certainty
      Temporality
      Experiencer
     */

    /* */
    public void setConTxt(String negS, String hypS, String expS, String hisS, 
            String sStr, int cTs, int cTe, HashMap<String, String> f) {

        String neg = "affirm";
        String hyp = "certain";
        String exp = "patient";
        String his = "present";

        ArrayList<Span> sTs = SimpleParser.tokenizeOnWhitespaces(sStr);
        ArrayList<String> res = fc.processContext(sTs, cTs, cTe, sStr, 30);

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

        PrintStream out = new PrintStream( new FileOutputStream( outputDirectoryName + "/" + fvFileName ));

        for (int i=0; i< fvs.size(); i++) { 
            StringBuffer sb = new StringBuffer();
            HashMap<String, String> f = fvs.get(i);

            appendL(sb, f.get("w"), "w");
            appendL(sb, f.get("wB"), "wB");

            appendL(sb, f.get("pw3"), "pw3");
            appendL(sb, f.get("pwB3"), "pwB3");
            appendL(sb, f.get("pw7"), "pw7");
            appendL(sb, f.get("pwS"), "pwS");

            appendL(sb, f.get("nw3"), "nw3");
            appendL(sb, f.get("nwB3"), "nwB3");
            appendL(sb, f.get("nw7"), "nw7");
            appendL(sb, f.get("nwS"), "nwS");

            append(sb, f.get("p"), "p");
            append(sb, f.get("pp3"), "pp3");
            append(sb, f.get("np3"), "np3");

            appendL(sb, f.get("s"), "s");

            append(sb, f.get("neg"), "neg");
            append(sb, f.get("hyp"), "hyp");
            append(sb, f.get("exp"), "exp");
            append(sb, f.get("his"), "his");

            out.println(f.get("attr") + "||" + sb.toString());		    	        
        }
        out.flush();
        out.close();

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

}
