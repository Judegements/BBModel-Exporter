// cd /storage/emulated/0/AcodeX/Test
// mvn compile exec:java

package test;

import java.io.*;

public class Main {
	public static void main(String[] args) {
		try {
			System.out.println(new BBModel(new FileReader("model.bbmodel")).export());
		} catch (IOException e) {
			
		}
	}
}
