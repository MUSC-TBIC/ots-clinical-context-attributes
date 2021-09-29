/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;

/**
 *
 * @author Owner
 */
public class EvalAlAttrMulti {

    public static void main(String[] args) throws FileNotFoundException {

        // TODO code application logic here

        String refDir = args[0];
        String ansDir = args[1];

        if( !refDir.endsWith("/") ) {
            refDir += "/";
        }
        if (!ansDir.endsWith("/")) {
            ansDir += "/";
        }
        
        ArrayList<String> refFileList = new ArrayList<String>();
        listFile(refDir, refFileList, "ann");
        
        HashSet<String> ref = new HashSet<String>();
        HashSet<String> ans = new HashSet<String>();
        
        for (String fileName : refFileList) {
            readCons(refDir, fileName, ref);
            readCons(ansDir, fileName, ans);
        }
        
        setData(ref, ans);
        
    }
    
    public static void eval(String refDir, String ansDir) throws FileNotFoundException {

        ArrayList<String> refFileList = new ArrayList<String>();
        listFile(refDir, refFileList, "ann");
        
        HashSet<String> ref = new HashSet<String>();
        HashSet<String> ans = new HashSet<String>();
        
        for (String fileName : refFileList) {
            readCons(refDir, fileName, ref);
            readCons(ansDir, fileName, ans);
        }
        
        setData(ref, ans);
        
    }

    public static void readCons(String inDir, String fileName, HashSet<String> outs) {
        
        File f = new File(inDir, fileName);
        
        if (!f.exists()) {
            //System.out.println(inDir + " " + fileName);
            return;
        }
        
        ArrayList<String> ins = new ArrayList<>();
        String str = "";
        {
            BufferedReader txtin = null;
            try {
                txtin = new BufferedReader(new FileReader(inDir + fileName));
                //T1    Drug 705 714    laryngeal
                while ((str = txtin.readLine()) != null) {
                    ins.add(str);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                try {
                    txtin.close();
                } catch (Exception ex) {

               }
            }
        }
        
        HashMap<String, String> cMap = new HashMap<>();
        for (String in : ins) {
            //T1        problem 506 520 l shoulder inj

            if (in.startsWith("T")) {
                String cA[] = in.split("\t", 3);
                String id = cA[0];
                
                String cAA[] = cA[1].split(" ", 3);
                int b = Integer.parseInt(cAA[1]);
                int e = Integer.parseInt(cAA[2]);

                cMap.put(id, b + " " + e);
            }
        }

        for (String in : ins) {
            //A4        NotPatient T9

            if (in.startsWith("A")) {
                String cA[] = in.split("\t", 2);
                
                String cAA[] = cA[1].split(" ", 2);
                String id = cAA[1];
                String attr = cAA[0];
                
                if (cMap.containsKey(id)) {
                    // file b e attr
                    outs.add(fileName + " " + cMap.get(id) + " " + attr);
                }
            }
        }
        
    }
    
    private static void updateMap(TreeMap<String, Double> map, String key) {
        if (map.containsKey(key)) {
            double val = map.get(key) + 1;
            map.put(key, val);
        } else {
            map.put(key, 1.0);
        }
    }

    public static void setData(HashSet<String> ref, 
            HashSet<String> ans) {

        TreeMap<String, Double> refNum = new TreeMap<String, Double>();
        TreeMap<String, Double> ansNum = new TreeMap<String, Double>();
        TreeMap<String, Double> corNum = new TreeMap<String, Double>();
        
        for (String k : ref) {
            String tmp = k.split(" ")[3];
            updateMap(refNum, "All");
            updateMap(refNum, tmp);
        }
        for (String k: ans) {
            String tmp = k.split(" ")[3];
            updateMap(ansNum, "All");
            updateMap(ansNum, tmp);
        }
        
        for (String k : ref) {
            if (ans.contains(k)) {
                updateMap(corNum, "All");
                updateMap(corNum, k.split(" ")[3]);
            }
        }
        
        NumberFormat nf = new DecimalFormat("###.00");
        
        double mR = 0;
        double mP = 0;
        double mF = 0;
        double mCnt = 0;

        for (String key : refNum.keySet()) {
            double rN = 0;
            double aN = 0;
            double cN = 0;
            rN = refNum.get(key);
            if (ansNum.containsKey(key)) {
                aN= ansNum.get(key);
            }
            if (corNum.containsKey(key)) {
                cN= corNum.get(key);
            }
            HashMap<String, Double> tmp = calScore(rN, aN, cN);
            
            System.out.print(key + "\tr:" + nf.format(tmp.get("recall")));
            System.out.print(" p:" + nf.format(tmp.get("precision")));
            System.out.print(" f:" + nf.format(tmp.get("fscore")));
            System.out.println(" " + rN + " " + aN + " " + cN);
            
            if (!key.equals("All")) {
                mR += tmp.get("recall");
                mP += tmp.get("precision");
                mCnt++;
            }
        }
        
        mR /= mCnt;
        mP /= mCnt;
        if (mP + mR == 0) {
            mF = 0;
        } else {
            mF = (2 * mP * mR)/(mP + mR);
        }
        System.out.print("macro" + "\tr:" + nf.format(mR));
        System.out.print(" p:" + nf.format(mP));
        System.out.println(" f:" + nf.format(mF));
    }
    
    public static HashMap<String, Double> calScore(double numRef, double numAns, double numCrt) {
        double r = 0;
        double p = 0;
        double f = 0;

        if (numRef == 0) {
            r = 0;
        } else {
            r = (double)numCrt/numRef;
        }

        if (numAns == 0) {
            p = 0;
        } else {
            p = (double)numCrt/numAns;
        }

        if (p + r == 0) {
            f = 0;
        } else {
            f = (2 * p * r)/(p + r);
        }

        r *= 100;
        p*=100;
        f*=100;

        HashMap<String, Double> map = new HashMap();
        map.put("recall", r);
        map.put("precision", p);
        map.put("fscore", f);

        return map;
    }

    public static void listFile(String dirName, ArrayList<String> fileList, String ext) {
        File dir = new File(dirName);

        String[] children = dir.list();
        if (children == null) {
            return;
        } else {
            for (int i = 0; i < children.length; i++) {
                // Get filename
                String filename = children[i];
                if (filename.endsWith("." + ext)) {
                    fileList.add(filename);
                }
            }
        }

    }
    
    public static void readRefData(String file, ArrayList<Double> ref) throws FileNotFoundException {

        String str = "";
        {
            BufferedReader txtin = new BufferedReader(new FileReader(file));
            try {
                while ((str = txtin.readLine()) != null) {

                    String tmpA[] = str.split(" ", 2);
                    double label = Double.parseDouble(tmpA[0]);
                    ref.add(label);
                }
                //System.out.println(idx + " " + map.size());

            } catch (Exception ex) {
                ex.printStackTrace ();
            } finally {
                try {
                    txtin.close();
                } catch (Exception ex) {
               }
            }
        }

    }

}
