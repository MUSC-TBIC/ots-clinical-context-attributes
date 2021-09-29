/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */



import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 *
 * @author jun
 */
public class AttrOutputAlMulti {

    /**
     * @param args the command line arguments
     */

    public static void main(String[] args) {
        
        //$predCV $trLbl $refDir $ansDir 0 

        String lblFile = args[0];
        String prFile = args[1];
        
        String refDir = args[2];
        String ansDir = args[3];
        String mode = args[4];
        
        String cFile = args[5];

        if( !refDir.endsWith("/") ) {
            refDir += "/";
        }
        if (!ansDir.endsWith("/")) {
            ansDir += "/";
        }
        
        HashMap<String, String> classM = new HashMap<>();
        readClassMapFile(cFile, classM);
        
        ArrayList<String> lbls = new ArrayList<>();
        readLbls(lblFile, lbls);
        HashMap<String, HashMap<String, String>> fOuts = new HashMap<>();
        
        readPr(prFile, lbls, fOuts, mode);
        
        ArrayList<String> refFileList = new ArrayList<String>();
        listFile(refDir, refFileList, "ann");
        
        for (String fileName : refFileList) {
            ArrayList<String> out = new ArrayList<>();
            
            int aI = 1;
            readRefCons(refDir, fileName, out);
            String fN = fileName.replace(".ann", ".txt");
            String fNWithoutSuffix = fileName.replace(".ann", "");
            ArrayList<String> tmp = new ArrayList<>();
            if (fOuts.containsKey(fN)) {
                addAttr( out , tmp , fOuts.get( fN ) , classM , aI );
                out.addAll( tmp );
            } else if( fOuts.containsKey( fNWithoutSuffix ) ) {
                addAttr( out , tmp , fOuts.get( fNWithoutSuffix ) , classM , aI );
                out.addAll( tmp );
            }

            writeFile(ansDir + fileName, out);
        }
        
    }
    
    public static void readClassMapFile(String file, HashMap<String, String> map) {

        String str = "";
        {
            BufferedReader txtin = null;
            try {

                txtin = new BufferedReader(new FileReader(file));
                while ((str = txtin.readLine()) != null) {
                    String strA[] = str.split(" ");
                    map.put(strA[1], strA[0]);
                    //Negated 1 => 1 Negated
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
    
    private static void addAttr(ArrayList<String> out, ArrayList<String> tmp, HashMap<String, String> attrs, 
        HashMap<String, String> classM, int aI) {
    
        HashMap<String, String> cMap = new HashMap<>();
        
        for (String str : out) {
            //T1        problem 506 520         l shoulder inj

            if (str.startsWith("T")) {
                String cA[] = str.split("\t", 3);
                String id = cA[0];
                
                String cAA[] = cA[1].split(" ", 3);
                int b = Integer.parseInt(cAA[1]);
                int e = Integer.parseInt(cAA[2]);

                cMap.put(b + " " + e, id);
            }
        }

        for (String span : cMap.keySet()) {
            // span check
            if (attrs.containsKey(span)) {
                String cLbl = classM.get(attrs.get(span));
                //A4    NotPatient T9
                tmp.add("A" + aI + "\t" + cLbl + " " + cMap.get(span));
                aI++;
            }
        }
        
    }
    
    public static void readLbls(String file, ArrayList<String> list) {

        String str = "";
        {
            BufferedReader txtin = null;
            try {
                txtin = new BufferedReader(new FileReader(file));

                while ((str = txtin.readLine()) != null) {
                    list.add(str);
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
    }
    
    public static void readPr(String file, ArrayList<String> lbls, HashMap<String, HashMap<String, String>> map, String mode) {

        String str = "";
        {
            BufferedReader txtin = null;
            try {
                txtin = new BufferedReader(new FileReader(file));

                int idx = 0;
                int pCnt = 0;
                while ((str = txtin.readLine()) != null) {
                    int l = 0;
                    
                    if (mode.equals("0")) {
                        String s = str.split(" ")[1];
                        double sd = Double.parseDouble(s);
                        l = (int) sd;
                    } else {
                        double sd = Double.parseDouble(str);
                        l = (int) sd;
                    }
                    
                    String ls = Integer.toString(l);
                        String lbl = lbls.get(idx);
                        String[] info = lbl.split(" ", 2);
                        String f = info[0];
                        if (!map.containsKey(f)) {
                            HashMap<String, String> tmp = new HashMap<>();
                            tmp.put(info[1], ls);
                            map.put(f, tmp);
                        } else {
                            map.get(f).put(info[1], ls);
                        }
                        pCnt++;
                        
                    idx++;
                }
                
                System.out.println(idx + " " + pCnt);
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                try {
                    txtin.close();
                } catch (Exception ex) {

               }
            }
        }
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

    public static void readRefCons(String inDir, String fileName, ArrayList<String> outs) {
        
        String str = "";
        {
            BufferedReader txtin = null;
            try {
                txtin = new BufferedReader(new FileReader(inDir + fileName));
                //T1    Drug 705 714    laryngeal
                while ((str = txtin.readLine()) != null) {
                    if (!(str.startsWith("T") || str.startsWith("N"))) {
                        continue;
                    }
                    outs.add(str);
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
        
    }
    
    public static int readAnsCons(String inDir, String fileName, ArrayList<String> outs) {

        int aI = -1;
        String str = "";
        {
            BufferedReader txtin = null;
            try {
                txtin = new BufferedReader(new FileReader(inDir + fileName));
                //T1    Drug 705 714    laryngeal
                while ((str = txtin.readLine()) != null) {
                    outs.add(str);
                    if (str.startsWith("A")) {
                        int tAI = Integer.parseInt(str.split("\t", 2)[0].replace("A", ""));
                        if (tAI > aI) {
                            aI = tAI;
                        }
                    }
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
        
        return aI + 1;
    }    

    public static PrintWriter getPrintWriter (String file)
    throws IOException {
        return new PrintWriter (new BufferedWriter
                (new FileWriter(file)));
    }

    public static void writeFile(String name, ArrayList<String> inst) {

        try {

            PrintWriter out = getPrintWriter(name);
            for (String str: inst) {
                out.println(str);
            }
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace ();
        }
        //System.out.println(idx.size());
    }
}
