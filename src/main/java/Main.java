import javax.swing.*;
import java.io.*;
import java.util.*;
public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println(args[0]);
        FileReader fr1 = new FileReader("/Users/saiprasad/input_1.csv");
        FileReader fr2 = new FileReader("/Users/saiprasad/input_2.csv");
        BufferedReader br1 = new BufferedReader(fr1);
        BufferedReader br2 = new BufferedReader(fr2);
        String heading1 = br1.readLine();
        String heading2 = br2.readLine();
        String[] head1 = heading1.split(",", 4);
        String[] head2 = heading2.split(",", 4);
        List<String[]> list1 = new ArrayList<>();
        List<String[]> list2 = new ArrayList<>();
        String str1 = "";
        while ((str1 = br1.readLine()) != null) {
            list1.add(str1.split(",", 4));
        }
        br1.close();
        fr1.close();
        str1 = "";
        while ((str1 = br2.readLine()) != null) {
            list2.add(str1.split(",", 4));
        }
        br2.close();
        fr2.close();

        Map<String, Integer> hmap1 = new HashMap<>();
        Map<String, Integer> hmap2 = new HashMap<>();

        for (int i = 0; i < list1.size(); i++) {
            String[] temp = list1.get(i);
            hmap1.put(temp[0], i);
        }
        for (int i = 0; i < list2.size(); i++) {
            String[] temp = list2.get(i);
            hmap2.put(temp[0], i);
        }

        FileWriter fw = new FileWriter("/Users/saiprasad/final_01.csv");
        BufferedWriter bw = new BufferedWriter(fw);
        int index1 = 0;
        int index2 = 0;
        for (Map.Entry<String, Integer> map : hmap1.entrySet()) {
            String temp1 = map.getKey();
            index1 = map.getValue();
            if (hmap2.get(temp1) != null) {
                index2 = hmap2.get(temp1);
                hmap2.remove(temp1);
                bw.write(temp1);
                bw.write(",");

                String[] curr1 = list1.get(index1);
                String[] curr2 = list2.get(index2);
                for (int i = 1; i < 4; i++) {

                    if (curr1[i] != null && curr2[i] != null) {
                        bw.write(curr1[i]);
                        if (i < 2)
                            bw.write(',');
                    } else if (curr1[i] == null && curr2[i] != null) {
                        bw.write(curr2[i]);
                        bw.write("(f1)");
                        if (i < 2)
                            bw.write(',');
                    } else if (curr1[i] != null && curr2[i] == null) {
                        bw.write(curr1[i]);
                        bw.write("(f2)");
                        if (i < 2)
                            bw.write(',');
                    }


                }

            } else {
                bw.write(temp1);
                bw.write("(f2)");
                bw.write(",");

                String[] curr1 = list1.get(index1);
                for (int i = 1; i < 4; i++) {

                    if (curr1[i] != null) {
                        bw.write(curr1[i]);
                        if (i < 2)
                            bw.write(',');
                    } else {
                        bw.write("(f1)(f2)");
                        if (i < 2)
                            bw.write(',');
                    }


                }

            }
            bw.newLine();
        }
        for (Map.Entry<String, Integer> map : hmap2.entrySet()) {
            String temp1 = map.getKey();
            index1 = map.getValue();
            bw.write(temp1);
            bw.write("(f1)");
            bw.write(",");
            String[] curr1 = list2.get(index1);
            for (int i = 1; i < 4; i++) {

                if (curr1[i] != null) {
                    bw.write(curr1[i]);
                    if (i < 2)
                        bw.write(',');
                } else {
                    bw.write("(f1)(f2)");
                    if (i < 2)
                        bw.write(',');
                }
            }
            bw.newLine();
        }
      bw.close();
      fw.close();

    }


}


