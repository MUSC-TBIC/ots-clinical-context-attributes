
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.TreeSet;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author jun
 */
public class LIBTrFV2AttrMulti {

    /**
     * @param args the command line arguments
     */

    public static void main(String[] args) {

        // TODO code application logic here
        boolean test = false; // true: test, for the real, set false

        String trainFile = "";
        String fvFile = "";
        String idxFile = "";
        String cutoffS = "";
        String ifTrainS = "";
        String fFile = "";
        
        String cFile = "";
        
        boolean ifTrain = false;

        int cutoff = 0;

        if (test) {
            trainFile = "/Users/jun/Documents/work/liblinear_rel/rel_fv_tr_treat.txt";
            fvFile = "/Users/jun/Documents/work/liblinear_rel/tr_fv";
            idxFile = "/Users/jun/Documents/work/liblinear_rel/tr_bow";

            if (!ifTrain) {
                trainFile = "/Users/jun/Documents/work/liblinear_rel/rel_fv_ts_treat.txt";
                fvFile = "/Users/jun/Documents/work/liblinear_rel/ts_fv";
                idxFile = "/Users/jun/Documents/work/liblinear_rel/tr_bow";
            }
            cutoffS = "0";
        } else {
            ifTrainS = args[0];
            trainFile = args[1];
            fvFile = args[2];
            idxFile = args[3];
            cutoffS = args[4];
            fFile = args[5];
            
            cFile = args[6];
        }

        if (ifTrainS.equals("1")) {
            ifTrain = true;
        }

        cutoff = Integer.parseInt(cutoffS);
        
        ArrayList<String> inst = new ArrayList<String>();
        TreeMap<String, Integer> bowMap = new TreeMap<String, Integer>();
        TreeMap<String, Integer> bowCnt = new TreeMap<String, Integer>();
        ArrayList<String> outs = new ArrayList<String>();

        HashSet<String> ft = new HashSet<String>();
        readFTFile(fFile, ft);
        
        HashMap<String, String> classM = new HashMap<>();
        readClassMapFile(cFile, classM);
        
        if (ifTrain) {
            bowMap.put("unk", 0);
            readTrData(trainFile, inst, bowMap, bowCnt, ft);
            writeMapToFile(idxFile, bowMap, bowCnt, cutoff);
        } else {
            readMap(idxFile, bowMap);
            readTsData(trainFile, inst);
        }

        if (ifTrain) {
            indexUNK(inst, bowMap, bowCnt, cutoff, outs, ft, classM);
        } else {
            index(inst, bowMap, outs, ft, classM);
        }
        writeListToFile(fvFile, outs);
    }

    public static void readClassMapFile(String file, HashMap<String, String> map) {

        String str = "";
        {
            BufferedReader txtin = null;
            try {

                txtin = new BufferedReader(new FileReader(file));
                while ((str = txtin.readLine()) != null) {
                    String strA[] = str.split(" ");
                    map.put(strA[0], strA[1]);
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
        //System.out.println(list.size());

    }
    
    public static void index(ArrayList<String> inst,
            TreeMap<String, Integer> bowMap, ArrayList<String> outs, HashSet<String> ft, HashMap<String, String> classM) {


        for (int i = 0; i < inst.size(); i++) {
            String str = inst.get(i);
            String strA[] = str.split("\\|\\|", 2);
            String lbl = strA[0];
            
            if (classM.containsKey(lbl)) {
                lbl = classM.get(lbl);
            } else {
                System.out.println("class label not contains !!! " + lbl);
            }            
            
            String data = strA[1].trim();

            String dataA[] = data.split("\\|\\|");
                
            TreeSet<Integer> iSet = new TreeSet<Integer>();

            for (int j = 0; j < dataA.length; j++) {
                String tok = dataA[j];
                String tokH = tok.split("_")[0];
                if (!ft.contains(tokH)) {
                    continue;
                }

                if (bowMap.containsKey(tok)) {
                    iSet.add(bowMap.get(tok));
                } else {
                    //iSet.add(bowMap.get("unk_" + tokH));
                    
                    //iSet.add(bowMap.get("unk"));
                }
            }

            StringBuilder sb = new StringBuilder();
            sb.append(lbl).append(" ");
            //2 3:1
            Iterator<Integer> it = iSet.iterator();
            while (it.hasNext()) {
                //int key = it.next() + 1; //
                int key = it.next() + 1001; // for sent meta info
                
                sb.append(key).append(":1 ");
            }

            outs.add(sb.toString().trim());
        }
    }

    public static void indexUNK(ArrayList<String> inst,
            TreeMap<String, Integer> bowMap, TreeMap<String, Integer> bowCnt, int cutoff, ArrayList<String> outs,
            HashSet<String> ft, HashMap<String, String> classM) {


        for (int i = 0; i < inst.size(); i++) {
            String str = inst.get(i);
            String strA[] = str.split("\\|\\|", 2);
            String lbl = strA[0];
            
            if (classM.containsKey(lbl)) {
                lbl = classM.get(lbl);
            } else {
                System.out.println("class label not contains !!! " + lbl);
            }            
            
            String data = strA[1].trim();

            String dataA[] = data.split("\\|\\|");

            TreeSet<Integer> iSet = new TreeSet<Integer>();

            for (int j = 0; j < dataA.length; j++) {
                String tok = dataA[j];
                String tokH = tok.split("_")[0];
                if (!ft.contains(tokH)) {
                    continue;
                }
                
                if (bowCnt.get(tok) < cutoff) { // 1 : equal or more than 1 
                    //iSet.add(bowMap.get("unk_" + tokH));
                    
                    //iSet.add(bowMap.get("unk"));
                } else {
                    iSet.add(bowMap.get(tok));                    
                } 

                // to keep features < cutoff
                //iSet.add(bowMap.get(tok));                    
                
            }

            StringBuilder sb = new StringBuilder();
            sb.append(lbl).append(" ");
            //2 3:1
            Iterator<Integer> it = iSet.iterator();
            while (it.hasNext()) {
                //int key = it.next() + 1; //
                int key = it.next() + 1001; // for sent meta info
                sb.append(key).append(":1 ");
            }

            outs.add(sb.toString().trim());
        }
    }

    public static void readTrData(String file, ArrayList<String> inst,
            TreeMap<String, Integer> bowMap, TreeMap<String, Integer> bowCnt, HashSet<String> ft) {

        String str = "";
        {
            BufferedReader txtin = null;
            try {

                TreeSet<String> unkSet = new TreeSet<String>();

                txtin = new BufferedReader(new FileReader(file));
                while ((str = txtin.readLine()) != null) {
                    inst.add(str);
                    String strA[] = str.split("\\|\\|", 2);
                    String data = strA[1].trim();
                    String dataA[] = data.split("\\|\\|");

                    TreeSet<String> tSet = new TreeSet<String>();
                    for (int i = 0; i < dataA.length; i++) {
                        String tok = dataA[i];

                        String tokH = tok.split("_", 2)[0];
                        
                        if (!ft.contains(tokH)) {
                            //System.out.println(tokH);
                            continue;
                        }

                        if (tSet.contains(tok)) {
                            continue;
                        } else {
                            tSet.add(tok);
                        }

                        if (bowMap.containsKey(tok)) {
                            int cnt = bowCnt.get(tok);
                            cnt++;
                            bowCnt.put(tok, cnt);
                        } else {
                            int idx = bowMap.size();
                            bowMap.put(tok, idx);
                            bowCnt.put(tok, 1);
                        }
                        /* */
                        String tokA[] = tok.split("_", 2);
                        if (!unkSet.contains(tokA[0])) {
                            unkSet.add("unk_" + tokA[0]);
                        }
                        /* */        
                    }
                }
                /* */
                Iterator<String> it = unkSet.iterator();
                while (it.hasNext()) {
                    String key = it.next();
                    if (!bowMap.containsKey(key)) {
                        int idx = bowMap.size();
                        bowMap.put(key, idx);
                    }
                }
                /* */


            } catch (Exception ex) {
                System.err.println(ex.getMessage());
            } finally {
                try {
                    txtin.close();
                } catch (Exception ex) {
               }
            }
        }
        //System.out.println(list.size());

    }

    public static void readMap(String file, TreeMap<String, Integer> bowMap) {

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
        //System.out.println(list.size());

    }

    public static void readFTFile(String file, HashSet<String> set) {

        String str = "";
        {
            BufferedReader txtin = null;
            try {

                txtin = new BufferedReader(new FileReader(file));
                while ((str = txtin.readLine()) != null) {
                    if (str.startsWith("#")) {
                        continue;
                    }
                    if (str.trim().isEmpty()) {
                        continue;
                    }
                    set.add(str.trim());
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
        //System.out.println(list.size());

    }
    
    public static void readTsData(String file, ArrayList<String> inst) {

        String str = "";
        {
            BufferedReader txtin = null;
            try {

                txtin = new BufferedReader(new FileReader(file));
                while ((str = txtin.readLine()) != null) {
                    inst.add(str);
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
        //System.out.println(list.size());

    }
    
    public static PrintWriter getPrintWriter (String file)
    throws IOException {
        return new PrintWriter (new BufferedWriter
                (new FileWriter(file)));
    }

    public static void writeMapToFile(String file,
            TreeMap<String, Integer> bowMap, TreeMap<String, Integer> bowCnt, int cutoff) {

        try {
            PrintWriter out = getPrintWriter(file);

            Iterator<String> it = bowMap.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                /*
                if (!key.startsWith("unk")) {
                    if (bowCnt.get(key) >= cutoff) { // 1 : equal or more than 1 
                        out.println(key + "||" + bowMap.get(key));
                    }
                } else {
                    out.println(key + "||" + bowMap.get(key));                    
                } 
                */

                // keep all feature 
                out.println(key + "||" + bowMap.get(key));                    
                        
            }
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace ();
        }
        //System.out.println(idx.size());
    }

    public static void writeListToFile(String file, ArrayList<String> list) {

        try {
            PrintWriter out = getPrintWriter(file);

            for (int i = 0; i< list.size(); i++) {
                out.println(list.get(i));
            }
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace ();
        }
        //System.out.println(idx.size());
    }

}
