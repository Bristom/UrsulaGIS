package tasks.old;

import java.lang.reflect.Array;
import java.util.Random;
import no.uib.cipr.matrix.*;



public class BiclusteringTask {
	/**
	 * En la matriz de entrada, va un cluster por columna (cada fila es un punto/pixel).
	 * @param A: matriz de pertenencia de los puntos que son las filas a los clusters que son las columnas
	 * @param maxBiclusterNum
	 * @param sharePoints determina si los clusters finales pueden compartir puntos o no
	 * @param minSizeRows
	 * @param minSizeCols
	 */
	public Matrix[] biclusterNMF(Matrix A,int maxBiclusterNum,boolean sharePoints,int minSizeRows,int minSizeCols){
		//	%BICLUSTER Summary of this function goes here
		//	%   Detailed explanation goes here

	

//		DenseMatrixBool R = new DenseMatrixBool(A.numRows,maxBiclusterNum);
//		DenseMatrixBool C = new DenseMatrixBool(maxBiclusterNum,A.numCols);

		Equation eq = new Equation();
		eq.alias(A,"A",maxBiclusterNum,"N");
		eq.process("R=zeros(size(A, 1), N)");
		eq.process("C=zeros(N, size(A, 2))");

		for(int i=1;i<maxBiclusterNum;i++){
			if(nnz(A) == 0){// nnz(A)  Number of nonzero matrix elements
				//A(:,j:k)	is A(:,j), A(:,j+1),...,A(:,k).
				//A(:,j) is the jth column of A.			
				eq.process("R(:, i:end) = []");
				eq.process("C(i:end, :) = []");
				//R(:, i:end) = [];//completa R con [] desde la columna i hasta end
				//C(i:end, :) = [];//completa R con [] desde la fila i hasta end
				break;
			}
			
			int	lambdaU = 1;
			int lambdaV = 1;
			int	lambdaE = 1;
			SimpleMatrix[] nmfRet =nmfRobust(eq.lookupMatrix("A"), 1, lambdaU, lambdaV, lambdaE);
			SimpleMatrix uR = nmfRet[0];
			SimpleMatrix vR = nmfRet[1];

			if( nnz(uR) < minSizeRows || nnz(vR) < minSizeCols){
				eq.process("R(:, i:end) = []");
				eq.process("C(i:end, :) = []");
				break  ;      
			}	
			
			eq.alias("uR",uR,"vR",vR);
			eq.process("rows = uR>0");
			eq.process("cols = vR>0");

			// vuelve a convertir uR y vR en un vector de relacion
			
//			boolean[]  rows = uR>0;//A>B returns a logical array with elements set to logical 1 (true) where A is greater than B; otherwise, the element is logical 0 (false). The test compares only the real part of numeric arrays. gt returns logical 0 (false) where A or B have NaN or undefined categorical elements.
//			boolean[]  cols = vR>0;
//			eq.alias("rows",rows);
//			eq.alias("cols",cols);

			
			eq.process("R(:, i) = rows");
			eq.process("C(i, :) = cols");
//			R(:, i) = rows;
//			C(i, :) = cols;

			if( !sharePoints){				
				eq.process("A(rows, :) = 0");
				//A(rows, :) = 0;
			}
			eq.process("A(:, cols) = 0");
			//A(:, cols) = 0;

			System.out.print(i+" ");//imprime la celda
			//  fprintf('%d  ', i);
		}

		//			end
		//			fprintf('\n');//cambia la fila
		//			end
		Matrix R =eq.lookupMatrix("R");
		Matrix C =eq.lookupMatrix("C");
		Matrix[] rcArray = new Matrix[2];
		rcArray[0]=R;
		rcArray[1]=C;
		return rcArray;
	}
	
private int nnz(SimpleMatrix uR) {
	//CommonOps.elements(arg0, arg1, arg2)
	Equation eq = new Equation();
	eq.alias(uR,"M");
	eq.process("NUM_DATOS=nnz(A)");//no devuelve 
	return eq.lookupInteger("NUM_DATOS");
	}


/**
 * Calcula as matrizes fatores da matrix D (n x m)
 * @param d Matriz original
 * @param r N�mero de caracter�sticas
 * @return ResultadoNMF com matriz w ( n x r ) e H (r x m)
 */
	private SimpleMatrix[] nmfRobust(SimpleMatrix d, int r) {		
		int maxInteracoes = 5000;

		double oldObj, obj;
		int n = d.numRows();
		int m = d.numCols();
		SimpleMatrix w = initMatrizFator(n, r);
		SimpleMatrix h = initMatrizFator(r, m);

		for (int i = 0; i < maxInteracoes; i++) {
			
			oldObj = calculaFuncaoObjetivo(d, w, h);
			
			// calcula o produto apenas 1 vez
			SimpleMatrix wh = w.mult(h);
			
			SimpleMatrix wt = w.transpose();
			// atualiza fator H
			for (int x = 0; x < r; x++) {
				for (int y = 0; y < m; y++) {
					double value = (wt.mult(d).get(x, y))
							/ (wt.mult(wh).get(x, y));
					h.set(x, y, h.get(x, y) * value);
				}
			}

			// atualiza fator W
			for (int x = 0; x < n; x++) {
				for (int y = 0; y < r; y++) {
					SimpleMatrix ht = h.transpose();
					double value = (d.mult(ht).get(x, y))
							/ (wh.mult(ht).get(x, y));
					w.set(x, y, w.get(x, y) * value);
				}
			}
			
			
			obj = calculaFuncaoObjetivo(d, w, h);
			double erro = oldObj - obj;
	
		}
		
		//return new ResultadoNMF(w, h);
			    
//			    Matrix U = eq.lookupMatrix("U");
//			    Matrix V = eq.lookupMatrix("V");
		SimpleMatrix[] retUV =new SimpleMatrix[2];
			    retUV[0]=w;
			    retUV[1]=h;
				return retUV ;
	}

	/**
	 * Calcula o valor da fun��o objetivo
	 * @param d Matriz original
	 * @param w Matriz fator
	 * @param h Matriz fator
	 * @return Valor da fun��o objetivo
	 */
	static double calculaFuncaoObjetivo(SimpleMatrix d, SimpleMatrix w, SimpleMatrix h){
		SimpleMatrix wh = w.mult(h);
		SimpleMatrix minus = d.minus(wh);
		return NormOps.normP2(minus.getMatrix());
	}
	
	

	/**
	 * Inicializa a matriz fator n x r
	 * @param n Linhas
	 * @param r Colunas
	 * @return
	 */
	private static SimpleMatrix initMatrizFator(int n, int r) {
		return initMatrizFatorRandomica(n, r);
	}

	/**
	 * Inicializa a matriz fator com valores aleatorios
	 * @param n Linhas
	 * @param r Colunas
	 * @return
	 */
	private static SimpleMatrix initMatrizFatorRandomica(int n, int r) {
		return SimpleMatrix.random(n, r, 0, 1, new Random());
	}
/**
 * usar la libreria LAML para resolver el nmf con tolerancia
 * @author tomas
 *
 */
public class nmfLAML{
	/*
	 * # L1NMF

String dataMatrixFilePath = "CNN - DocTermCount.txt";

tic();
Matrix X = loadMatrixFromDocTermCountFile(dataMatrixFilePath);
X = Matlab.getTFIDF(X);
X = Matlab.normalizeByColumns(X);
X = X.transpose();

KMeansOptions kMeansOptions = new KMeansOptions();
kMeansOptions.nClus = 10;
kMeansOptions.maxIter = 50;
kMeansOptions.verbose = true;

KMeans KMeans = new KMeans(kMeansOptions);
KMeans.feedData(X);
// KMeans.initialize(null);
KMeans.clustering();

Matrix G0 = KMeans.getIndicatorMatrix();

// Matrix X = Data.loadSparseMatrix("X.txt");
G0 = loadDenseMatrix("G0.txt");
L1NMFOptions L1NMFOptions = new L1NMFOptions();
L1NMFOptions.nClus = 10;
L1NMFOptions.gamma = 1 * 0.0001;
L1NMFOptions.mu = 1 * 0.1;
L1NMFOptions.maxIter = 50;
L1NMFOptions.verbose = true;
L1NMFOptions.calc_OV = !true;
L1NMFOptions.epsilon = 1e-5;
Clustering L1NMF = new L1NMF(L1NMFOptions);
L1NMF.feedData(X);
// L1NMF.initialize(G0);

L1NMF.clustering(G0); // Use null for random initialization

System.out.format("Elapsed time: %.3f seconds\n", toc());

# Output

Iter 1: mse = 1.524 (0.030 secs)
Iter 2: mse = 0.816 (0.030 secs)
Iter 3: mse = 0.806 (0.030 secs)
Iter 4: mse = 0.805 (0.040 secs)
KMeans complete.
Iteration 10, delta G: 0.046591
Iteration 20, delta G: 0.047140
Iteration 30, delta G: 0.020651
Iteration 40, delta G: 0.010017
Iteration 50, delta G: 0.007973
Maximal iterations
Elapsed time: 3.933 seconds
	 */
}

}
