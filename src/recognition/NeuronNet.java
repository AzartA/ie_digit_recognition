package recognition;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.StringJoiner;
import java.util.logging.*;



/**
 * @version 6.3
 * @serial NEURONS_IN_LAYERS
 * @serial weights
 * @serial LAYERS
 */
public class NeuronNet implements Serializable {

	private static final long serialVersionUID = 328778L;
	private final int LAYERS;
	private final int[] NEURONS_IN_LAYERS;
	private double[][][] weights;
	transient public double[][][] deltaW; // non-rectangle matrix
	transient public double[][] inputNumbers;
	transient public int iInLength;
	transient private double[][] neurons;
	transient private double currErr;
	transient private Random rd;
	transient double rand;
	
	private RReLU relu;
	private Activation activ;
	//private Cost error;
	//private Regularization regFunc;
	//private Initialization init;
	
	protected static final Logger LOGGER = Logger.getLogger(NeuronNet.class.getName());
	
	

	public NeuronNet() {
		this(784, 10);
	}

	public NeuronNet(int... neuronsInLayers) {
		LAYERS = neuronsInLayers.length;
		NEURONS_IN_LAYERS = neuronsInLayers.clone();
		weights = new double[LAYERS - 1][][];
		rd = new Random(328778L);
		for (int l = 0; l < (LAYERS - 1); l++) {
			weights[l] = new double[NEURONS_IN_LAYERS[l + 1]][];
			for (int i = 0; i < NEURONS_IN_LAYERS[l + 1]; i++) {
				weights[l][i] = new double[NEURONS_IN_LAYERS[l] + 1];
				for (int j = 0; j < NEURONS_IN_LAYERS[l] + 1; j++) {
					weights[l][i][j] = rd.nextGaussian();
				}
			}
		}
		
	}
	
	private void SetWeights(int numberOfTrainingSets) {
		
		for (int l = 0; l < (LAYERS - 1); l++) {
			for (int i = 0; i < NEURONS_IN_LAYERS[l + 1]; i++) {
				for (int j = 0; j < NEURONS_IN_LAYERS[l] + 1; j++) {
					weights[l][i][j] = rd.nextGaussian()*Math.sqrt(2.0/(numberOfTrainingSets));//0.04472135954999579392818347337463;
				}
			}
		}
		
	}
	
	public void setActivation(Activation activ) {
		this.activ = activ;
		activ.net = this;
		if ("RReLU".equals(getActivationClassName())){
			relu = (RReLU)activ;
			SetWeights(relu.numberOfTrainingSets);
		}
	}
	
	public String getActivationClassName() {
		String[] s = activ.getClass().getName().split("\\.");
		return s[s.length-1];
	}
	
	public String getNeurons() {
		StringJoiner joiner = new StringJoiner(", ");
		for (int i : NEURONS_IN_LAYERS) {
			joiner.add(Integer.toString(i));
		}
		return joiner.toString();
	}

	
	public void saveToF() {

		try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("nnw5a.bin"))) {
			out.writeObject(this);
			LOGGER.fine("Saved successfully.");
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static NeuronNet loadFromF() {
		try (ObjectInputStream in = new ObjectInputStream(new FileInputStream("nnw5a.bin"))) {
			NeuronNet net = new NeuronNet();
			net = (NeuronNet) in.readObject();
			// this.setWeights(net.getWeights());
			LOGGER.fine("Loaded successfully.");
			return net;
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
		}
		return null;

	}

	public void loadInputNumbers(int countOfSamplesOfANumber, int startIndex) {
		Assets as = new Assets(); // load input numbers
		as = as.loadFromF();
		LOGGER.finest("loaded");
		inputNumbers = as.createTraningSet(countOfSamplesOfANumber, startIndex);
		iInLength = inputNumbers.length;
	}

	public int straightPropagation(double[] inputNumber) {
		neurons = new double[LAYERS][];
		neurons[0] = inputNumber.clone();
		neurons[0][inputNumber.length - 1] = 1.0;											// bias multiplier
		for (int l = 0; l < LAYERS - 1; l++) {
			
				neurons[l + 1] = MatrixMath.activateNeuron(neurons[l], weights[l], activ);
			
			if (l != LAYERS - 2) {
				neurons[l + 1] = Arrays.copyOf(neurons[l + 1], neurons[l + 1].length + 1);
				neurons[l + 1][neurons[l + 1].length - 1] = 1.0;
			}
		}
		return (int) inputNumber[inputNumber.length - 1];
	}

	public void backPropagation(int idealNumber, double eta, double lambda) {
		double[][] error = new double[2][];
		// last layer LAYERS-1
		error[0] = new double[NEURONS_IN_LAYERS[LAYERS - 1]];
		for (int out = 0; out < NEURONS_IN_LAYERS[LAYERS - 1]; out++) {
			error[0][out] = out == idealNumber ? (neurons[LAYERS - 1][out] - 1.0) : (neurons[LAYERS - 1][out]);		// error δ (∂E/∂z) of last layer = a-t, derivative of error function (quadratic) 
			currErr += error[0][out] * error[0][out] * 0.5;															// E = 1/2 * Σ(t-a)² error function (quadratic) ((t-a)² = (a-t)²)
			error[0][out] *= activ.deriv(neurons[LAYERS - 1][out]);								// ∂a/∂z = σ'(z) = σ(z)*(1-σ(z)) = a*(1-a), derivative of activation function (sigmoid) 

			for (int i = 0; i < weights[LAYERS - 2][0].length; i++) {
				deltaW[LAYERS - 2][out][i] -= eta * (error[0][out] * neurons[LAYERS - 2][i]							//
						+ lambda * weights[LAYERS - 2][out][i] / inputNumbers.length);								// 
			}
		}

		// hidden layers
		for (int l = LAYERS - 2; l > 0; l--) {
			error[1] = error[0].clone();
			error[0] = new double[NEURONS_IN_LAYERS[l]];
			for (int j = 0; j < NEURONS_IN_LAYERS[l]; j++) {
				for (int k = 0; k < NEURONS_IN_LAYERS[l + 1]; k++) {
					error[0][j] += weights[l][k][j] * error[1][k];							// error (δ = ∂E/∂z) of the layer = Σ( w * error (δ) of next layer) ...
				}
				error[0][j] *= activ.deriv(neurons[l][j]);							// ... * σ'(z) derivative of activation function (sigmoid) of the layer
				for (int i = 0; i < weights[l - 1][0].length; i++) {						
					deltaW[l - 1][j][i] -= eta	 											// ∂w = -η * [δ *a ...
							* (error[0][j] * neurons[l - 1][i] +
									//i==NEURONS_IN_LAYERS[l-1]? 0:								// not for biases, for weights only:
								lambda * weights[l - 1][j][i] / inputNumbers.length); 		// ... + λ * w / n], derivative of L2-regularization factor
				}																			
			}
		}

	}

	private void recountWeights(int batchSize) {
		for (int l = 0; l < (weights.length); l++) {
			for (int m = 0; m < (weights[l].length); m++) {
				for (int n = 0; n < (weights[l][m].length); n++) {
					weights[l][m][n] += deltaW[l][m][n] / batchSize;
				}
			}
		}
	}

	public void printArray(double[][] array) {
		String a = Arrays.deepToString(array);
		a = a.replace("],", "]\n");
		System.out.println(a);
		System.out.println();
	}

	public void printArray(double[][][] array) {
		String a = Arrays.deepToString(array);
		a = a.replace("],", "]\n");
		a = a.replace("]]", "]]\n");
		System.out.println(a);
		System.out.println();
	}

	public int getDigit(double[] inNeurons) {
		int digit = -1;
		double bestRes = -1000.0;
		if (!(relu==null)){
			rand = 1/relu.getMean();
		}
		straightPropagation(inNeurons);
		
		for (int i = 0; i < NEURONS_IN_LAYERS[LAYERS - 1]; i++) {
			if (neurons[LAYERS - 1][i] > bestRes) {
				bestRes = neurons[LAYERS - 1][i];
				digit = i;
			}
		}
		return digit;
	}

	public void selfLearning(int numberOfTranigSets, int ofset, int epoches, double eta, int batchSize, double lambda,
								double minErr, double minDErr) {
		
		LOGGER.log(Level.FINE, "The selflearning of Net starting with:\nNumber of training sets of each digit: {0};\n"+ 
								"Ofset in tranigbase: {1};\nNumber of epoches: {2};\nParametr η (eta): {3};\nThe size of a batch: {4};\n" +
								"Parametr λ (lambda): {5};\nThe minimal error of the Net: {6};\nThe minimal interval between last and next errors: {7}.",
								new Object[]{numberOfTranigSets, ofset, epoches, eta, batchSize, lambda, minErr, minDErr});
		
		double lastErr = 0;
		double dErr = 0;
		int epochN = 0;
		int trueRes;
		LOGGER.fine("Learning...");
		loadInputNumbers(numberOfTranigSets, ofset);
		List<double[]> iNums = Arrays.asList(inputNumbers);
		
		if (batchSize < 1 || batchSize > (numberOfTranigSets * 10)) {
			batchSize = numberOfTranigSets * 10;
		}

		do { 																// epoch cycle
			
			if (!(relu==null)){
				
				rand = rd.nextDouble();
				rand=rand*(1/relu.getMinOfDist()-1/relu.getMaxOfDist())+1/relu.getMaxOfDist();
			}
			
			lastErr = currErr;
			double reg = 0;
			currErr = 0;
			trueRes = 0;
			Collections.shuffle(iNums);										
			for (int b = 0; b < (iInLength + batchSize); b += batchSize) {	// batches iterations in input numbers set

				// deltaWeight initialization
				deltaW = new double[LAYERS - 1][][];
				for (int l = 0; l < (LAYERS - 1); l++) {
					deltaW[l] = new double[NEURONS_IN_LAYERS[l + 1]][];
					for (int i = 0; i < NEURONS_IN_LAYERS[l + 1]; i++) {
						deltaW[l][i] = new double[NEURONS_IN_LAYERS[l] + 1];
					}
				}
				// ---

				for (int bs = 0; (bs < batchSize) && ((b + bs) < iInLength); bs++) { // each batch processing
					int idealNumber = straightPropagation(iNums.get(b + bs));
					backPropagation(idealNumber, eta, lambda);
				}

				// recount weights
				recountWeights(batchSize);
			}

			// error block
			for (int i = 0; i < iNums.size(); i++) {
				if (iNums.get(i)[iNums.get(i).length - 1] == getDigit(iNums.get(i))) {
					trueRes++;
				};
			}

			// real error
			currErr /= iNums.size();

			// error regularization
			if (Math.abs(lambda) > Float.MIN_VALUE) {
				for (int l = 0; l < (weights.length); l++) {
					for (int m = 0; m < (weights[l].length); m++) {					
						for (int n = 0; n < (weights[l][m].length); n++) {			// length-1: only for weights, without biases!!!
							reg += weights[l][m][n] * weights[l][m][n];					// L2 regularization = Σ(w²)  ...
						}																
					}
				}
				currErr += lambda * reg / (2 * iNums.size());							// ...  * λ/(2 * n), regularization factor, 
			}																			// where λ is regularization parameter n is count of training sets

			epochN++;
			dErr = Math.abs(currErr - lastErr);
			double procent = (double) trueRes * 100 / iNums.size();
			LOGGER.log(Level.FINE, "Epoch # {0};\tcurrent E = {1};\tdelta E = {2}; recognition = {3}%",
					new Object[] { epochN, currErr, dErr, procent });
		} while (epochN < epoches && currErr > minErr && dErr >= minDErr);

		saveToF();
		LOGGER.fine("Saved to a file.");

	}

	public void selfLearning() {
		selfLearning(1000, 0, 100, 0.5, 10, 0.15, 0, 0);
	}

}


