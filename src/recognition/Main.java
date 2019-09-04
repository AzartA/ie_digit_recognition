package recognition;

import java.util.*;

public class Main {

	public static void main(String[] args) {
		 Weights wts = new Weights();
		 Scanner sc = new Scanner(System.in);
		 
		 System.out.println("1. Learn the network\r\n" + 
					"2. Guess a number\r\n"+
					"Enter your choice:");
			switch (sc.nextInt()) {
			case 1:
				sc.close();
				wts.selfLearning(); 
				break;
			case 2:
				System.out.println("Input grid:");
				int[] inNeurons = new int [16];		// input neurons
				sc.nextLine(); 
				for(int l = 0; l<5; l++) {
					  String line = sc.nextLine();
					  for(int i = 0; i<3;i++) {
						  inNeurons[l*3+i] = line.charAt(i) == 'X' ? 1 : -1; 
					  }
				 }
				 sc.close();
				 inNeurons[15] = 1; 					// bias multiplier
				 wts.loadFromF();
				 System.out.println("This number is " + wts.takeDigit(inNeurons));
				 break;
			default:
				System.out.println("Unknown comand.");
			}
	}

}
