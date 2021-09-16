package edu.musc.ce;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.ProcessTrace;

import edu.mayo.bmi.uima.core.util.DocumentIDAnnotationUtil;
import edu.musc.ce.type.BaseToken;
import edu.musc.ce.type.Concept;
import edu.musc.ce.type.Sentence;
import edu.utah.bmi.nlp.core.SimpleParser;
import edu.utah.bmi.nlp.core.Span;
import edu.utah.bmi.nlp.fastcontext.FastContext;

public class AlAttrFeatureGenCasConsumer extends CasConsumer_ImplBase {

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
    
    public void initialize() throws ResourceInitializationException 
    {
		
	outputDirectoryName = (String)getConfigParameterValue(PARAM_OUTPUTDIR);
	fvFileName = (String)getConfigParameterValue(PARAM_FVFILE);
	    
	iv_outputDirectory = new File(outputDirectoryName);
	if(!iv_outputDirectory.exists() || !iv_outputDirectory.isDirectory())
	    throw new ResourceInitializationException(
						      new Exception("Parameter setting 'OutputDirectory' does not point to an existing directory."));
	
	fvs = new ArrayList<HashMap<String, String>>();
	// Original source:
	//   - https://github.com/jianlins/FastContext/blob/master/conf/context.txt
        //fc = new FastContext("/Users/jun/Documents/work/project/FastContext/context.txt", false);
        fc = new FastContext( "../fastcontext_uima_2.9.0/resources/context.txt", false);
    }
    
    private void setSentMap(JCas jCas, HashMap<Integer, Integer> map, FSIterator<?> sentences, 
			    ArrayList<Sentence> sents, ArrayList<String> sentStrs) throws ResourceProcessException	{
	while (sentences.hasNext()) {
	    Sentence s = (Sentence) sentences.next();
	    int sNum = s.getSentNo() + 1;
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
	
    public void processCas(CAS cas) throws ResourceProcessException 
    {
	try 
	    {
			
		JCas jCas = cas.getJCas();
		String documentID = DocumentIDAnnotationUtil.getDocumentID(jCas);
			
		HashMap<Integer, Integer> sentMap = new HashMap<>(); 
		ArrayList<Sentence> sents = new ArrayList<>();
		ArrayList<String> sentStrs = new ArrayList<>();
		FSIterator<?> sentences = jCas.getAnnotationIndex(Sentence.type).iterator();
		setSentMap(jCas, sentMap, sentences, sents, sentStrs);			
			
		HashMap<Integer, Integer> tokMap = new HashMap<>(); 
		ArrayList<BaseToken> tokList = new ArrayList<>();
		FSIterator<?> tokens = jCas.getAnnotationIndex(BaseToken.type).iterator();
		setTokMap(tokMap, tokList, tokens);
			
		ArrayList<Concept> conList = new ArrayList<>();
		// TODO - allow concept types to focus on to be configured via parameter
		FSIterator<?> cons = jCas.getAnnotationIndex(Concept.type).iterator();
		while (cons.hasNext()) {
		    Concept c = (Concept) cons.next();
		    // for i2b2 2010 
		    if (c.getCType().equalsIgnoreCase("treatment") || c.getCType().equalsIgnoreCase("test")) {
			continue;
		    }
		    conList.add(c);
		}			
			
		for (int i = 0; i < conList.size(); i++) {
		    Concept c = conList.get(i);
				
		    int b = c.getBegin();
		    int e = c.getEnd();
				
		    String label = documentID + " " + b + " " + e;
		    String attr = c.getAttr();
				
		    String w = getLists(jCas, c, "w"); // word
		    String p = getLists(jCas, c, "p"); // pos
		    String s = getLists(jCas, c, "s"); // substring
		    // l lemma
				
		    String wB = getListsB(jCas, c, "w");
								
		    int ba = b; // actual begin span
		    while (tokMap.get(ba) == null && ba < e) {
			ba++;
		    }
		    int ea = e - 1; // actual begin span
		    while (tokMap.get(ea) == null && b <= ea) {
			ea--;
		    }
				
		    int sI = sentMap.get(ba); 
						
		    String pw3 = getListsP(jCas, tokList, tokMap.get(ba), 3, "w");
		    String nw3 = getListsN(jCas, tokList, tokMap.get(ea), 3, "w");
		    String pw7 = getListsP(jCas, tokList, tokMap.get(ba), 7, "w");
		    String nw7 = getListsN(jCas, tokList, tokMap.get(ea), 7, "w");
				
		    String pp3 = getListsP(jCas, tokList, tokMap.get(ba), 3, "p");
		    String np3 = getListsN(jCas, tokList, tokMap.get(ea), 3, "p");

		    String pwB3 = getListsPB(jCas, tokList, tokMap.get(ba), 3, "w");
		    String nwB3 = getListsNB(jCas, tokList, tokMap.get(ea), 3, "w");
				
		    String pwS = getPwordsS(jCas, tokList, tokMap.get(ba), sentMap, sI);
		    String nwS = getNwordsS(jCas, tokList, tokMap.get(ea), sentMap, sI);
				
		    /* */
		    String sStr = sentStrs.get(sI - 1);
		    int cSI = sI;
		    while (ea > sents.get(cSI - 1).getEnd()) {
			sStr += " " + sentStrs.get(cSI++);
			System.out.println(sStr);
		    }
				
		    int oTn = tokMap.get(sents.get(sI - 1).getBegin());
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
				
		    /* */
		    long startTime = System.nanoTime();				
		    setConTxt("neg", "hyp", "exp", "his", sStr, cTs, cTe, f);
		    long endTime = System.nanoTime();
		    /* */
				
		    ctxTime += endTime - startTime; 
				
		    fvs.add(f);

		}
			
	    }
	catch(Exception e)
	    {
		throw new ResourceProcessException(e);
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
    /*
      public void collectionProcessComplete(ProcessTrace arg0) throws ResourceProcessException, IOException 
      {
      System.out.println("Context Execution time in milliseconds : " + ctxTime / 1000000);		
		
      label(outputDirectoryName + "/" + fvFileName + ".lbl");
		
      PrintStream out = new PrintStream(new FileOutputStream(outputDirectoryName + "/" + fvFileName));
    	
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
    */
	
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
