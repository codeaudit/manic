package agents.manic;

import common.Matrix;
import common.json.JSONObject;
import common.Vec;
import java.util.Random;
import common.ITutor;

/// A model that maps from anticipated beliefs to contentment (or utility).
/// This model is trained by reinforcement from a mentor.
public class ContentmentModel {
	public Random rand;
	public NeuralNet model;
	public Matrix samples;
	public Matrix contentment;
	ITutor tutor;
	public int trainPos;
	public int trainSize;
	public int trainIters;
	public double learningRate;
	public int trainProgress;
	public double err;
	double[] targBuf;


	// General-purpose constructor
	ContentmentModel(int beliefDims, int total_layers, int queue_size, int trainItersPerPattern, Random r) {

		// Init the model
		rand = r;
		model = new NeuralNet();
		int hidden = Math.min(30, beliefDims * 10);
		model.layers.add(new LayerTanh(beliefDims, hidden));
		model.layers.add(new LayerTanh(hidden, 1));
		model.init(rand);

		// Init the buffers
		samples = new Matrix(queue_size, beliefDims);
		contentment = new Matrix(queue_size, 1);

		// Init the meta-parameters
		trainIters = trainItersPerPattern;
		learningRate = 0.03;
		targBuf = new double[1];
	}


	/// Unmarshaling constructor
	ContentmentModel(JSONObject obj, Random r) {
		rand = r;
		model = new NeuralNet((JSONObject)obj.get("model"));
		samples = new Matrix((JSONObject)obj.get("samples"));
		contentment = new Matrix((JSONObject)obj.get("contentment"));
		trainPos = ((Long)obj.get("trainPos")).intValue();
		trainSize = ((Long)obj.get("trainSize")).intValue();
		trainIters = ((Long)obj.get("trainIters")).intValue();
		learningRate = (Double)obj.get("learningRate");
		trainProgress = ((Long)obj.get("trainProgress")).intValue();
		err = (Double)obj.get("err");
		targBuf = new double[1];
	}


	/// Marshals this model to a JSON DOM.
	JSONObject marshal() {
		JSONObject obj = new JSONObject();
		obj.put("model", model.marshal());
		obj.put("samples", samples.marshal());
		obj.put("contentment", contentment.marshal());
		obj.put("trainPos", trainPos);
		obj.put("trainSize", trainSize);
		obj.put("trainIters", trainIters);
		obj.put("learningRate", learningRate);
		obj.put("trainProgress", trainProgress);
		obj.put("err", err);
		return obj;
	}


	void setTutor(ITutor t) {
		tutor = t;
	}


	/// Performs one pattern-presentation of stochastic gradient descent, and dynamically tunes the learning rate
	void doSomeTraining() {

		// Present a sample of beliefs and corresponding contentment for training
		int index = rand.nextInt(trainSize);
		model.regularize(learningRate, 0.000001);
		model.trainIncremental(samples.row(index), contentment.row(index), learningRate);
		err += Vec.squaredDistance(model.layers.get(model.layers.size() - 1).activation, contentment.row(index));
		if(++trainProgress >= 1000) {
			trainProgress = 0;
			//System.out.println("Contentment error: " + Double.toString(err / 1000.0));
			err = 0.0;
		}
	}


	/// Refines this model based on feedback from the mentor
	void trainIncremental(double[] sample_beliefs, double sample_contentment) {

		// Buffer the samples
		double[] dest = samples.row(trainPos);
		if(sample_beliefs.length != dest.length)
			throw new IllegalArgumentException("size mismatch");
		for(int i = 0; i < dest.length; i++)
			dest[i] = sample_beliefs[i];
		contentment.row(trainPos)[0] = sample_contentment;
		trainPos++;
		trainSize = Math.max(trainSize, trainPos);
		if(trainPos >= samples.rows())
			trainPos = 0;

		// Do a few iterations of stochastic gradient descent
		int iters = Math.min(trainIters, trainSize);
		for(int i = 0; i < iters; i++)
			doSomeTraining();
	}


	/// Computes the contentment of a particular belief vector
	public double evaluate(double[] beliefs) {
		if(tutor != null)
			return tutor.evaluateState(beliefs);
		double[] output = model.forwardProp(beliefs);
		return output[0];
	}
}


