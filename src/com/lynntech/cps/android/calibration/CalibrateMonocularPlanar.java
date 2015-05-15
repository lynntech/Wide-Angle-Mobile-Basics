/*
   Copyright 2015 Lynntech Inc. 
   Contact information:
   - Victor Palmer, 		victor.palmer[\AT]lynntech.com
   - Christian Bruccoleri, 	christian.bruccoleri[\AT]lynntech.com
   
   Research reported in this publication was supported by the National Eye Institute 
   of the National Institutes of Health under Award Number R43EY024800.
   The content is solely the responsibility of the authors and does not necessarily
   represent the official views of the National Institutes of Health.

   This code includes parts of BoofCV: https://github.com/lessthanoptimal/BoofCV
   
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

/**
 * Example of how to calibrate a single (monocular) camera using a high level interface that processes images of planar
 * calibration targets.  The entire calibration target must be observable in the image and for best results images
 * should be in focus and not blurred.  For a lower level example of camera calibration which processes a set of
 * observed calibration points see {@link ExampleCalibrateMonocularPlanar}.
 *
 * After processing both intrinsic camera parameters and lens distortion are estimated.  Chessboard
 * targets are demonstrated by this example. See calibration tutorial for a discussion of different target types
 * and how to collect good calibration images.
 *
 * All the image processing and calibration is taken care of inside of {@link CalibrateMonoPlanar}.  The code below
 * loads calibration images as inputs, calibrates, and saves results to an XML file.  See in code comments for tuning
 * and implementation issues.
 *
 * @see CalibrateMonoPlanar
 *
 * @author Peter Abeles
 */

package com.lynntech.cps.android.calibration;

import boofcv.abst.calib.CalibrateMonoPlanar;
import boofcv.abst.calib.ConfigChessboard;
import boofcv.abst.calib.PlanarCalibrationDetector;
import boofcv.alg.geo.calibration.PlanarCalibrationTarget;
import boofcv.factory.calib.FactoryPlanarCalibrationTarget;
import boofcv.struct.calib.IntrinsicParameters;
import boofcv.struct.image.ImageFloat32;
import java.util.ArrayList;
import java.util.List;


public class CalibrateMonocularPlanar implements Runnable {
	public static final int num_calibr_images = 30;
 
	// Detects the target and calibration point inside the target
	private PlanarCalibrationDetector detector;
 
	// Description of the target's physical dimension
	private PlanarCalibrationTarget target;
 
	// List of calibration images
	private List<ImageFloat32> images;
	
	/// Calibrated intrinsic parameters, it is null until the #process method executes successfully.
	private IntrinsicParameters intrinsic;
	
	/// Thread object used for asynchronous calibration.
	private Thread thread;
	
	/// An object used to notify the caller that the calibration is complete.
	private CalibrationListener calibrListener;
 
	// Many 3D operations assumed a right handed coordinate system with +Z pointing out of the image.
	// If the image coordinate system is left handed then the y-axis needs to be flipped to meet
	// that requirement.  Most of the time this is false.
	boolean flipY;
	
	/// Used to obtain a lock on the #intrinsic field.
	private Object intrinsicLock;

 
	/**
	 * Getter for the calibrated intrinsic parameters
	 * @return The intrinsic parameters if {@link #process()} executed successfully.
	 */
	public IntrinsicParameters getIntrinsic() {
		IntrinsicParameters intrs = null;
		if (intrinsic != null) {
			synchronized (intrinsicLock) {
				// make a copy
				intrs = new IntrinsicParameters(intrinsic);
			}
		}
		return intrs;
	}


	public CalibrateMonocularPlanar() {
		// Use the wrapper below for chess board targets.
		detector = FactoryPlanarCalibrationTarget.detectorChessboard(new ConfigChessboard(10, 7));
 
		// physical description
		target = FactoryPlanarCalibrationTarget.gridChess(10, 7, 31.5); // 10 cols x 7 rows, 31.5 mm target 
 
		// standard image format
		flipY = false;

		// set intrinsic parameters to null to indicate that calibration has not been performed yet
		intrinsic = null;
		
		// Initialize the list to contain the calibration images
		images = new ArrayList<ImageFloat32>(num_calibr_images);
		
		intrinsicLock = new Object(); 
	}
 
	/**
	 * Add an image to the calibration set.
	 * @param Image
	 */
	public void addImage(ImageFloat32 img) {
		if (images.size() < num_calibr_images) {
			images.add(img);
		}
		else {
			throw new RuntimeException("Max calibration images exceeded.");
		}
	}
 
	
	/**
	 * Number of images currently stored for calibration.
	 * @return The number of images stored for calibration.
	 */
	public int getStoredImagesCount() {
		return images.size();
	}
	
	
	/**
	 * Deletes all images currently stored for calibration.
	 */
	public void clear() {
		images.clear();
	}
	
	
	/**
	 * Perform the calibration using the images previously stored in the object.
	 * This function may be slow, therefore asynchronous invocation is recommended.
	 * @see #processAsync(CalibrationListener)
	 */
	public void process() {
 
		// Declare and setup the calibration algorithm
		CalibrateMonoPlanar calibrationAlg = new CalibrateMonoPlanar(detector, flipY);
 
		// tell it type type of target and which parameters to estimate
		calibrationAlg.configure(target, true, 2);
 
		int count = 0;
		for( ImageFloat32 img : images ) {
			//BufferedImage input = UtilImageIO.loadImage(n);
			if( img != null ) {
				count ++;
				//ImageFloat32 image = ConvertBufferedImage.convertFrom(input,(ImageFloat32)null);
				if( !calibrationAlg.addImage(img) )
					System.err.println("Failed to detect target in " + count);
			}
		}
		// process and compute intrinsic parameters
		intrinsic = calibrationAlg.process();
 
		// save results to a file and print out
		//UtilIO.saveXML(intrinsic, "intrinsic.xml");
 
		calibrationAlg.printStatistics();
		System.out.println();
		System.out.println("--- Intrinsic Parameters ---");
		System.out.println();
		intrinsic.print();
	}

	/**
	 * Start the calibration process asynchronously in a new thread.
	 * When the calibration is complete, the function onCalibrationCompleted is invoked
	 * by the calibration thread.
	 * If this method is invoked while the calibration thread is already running, 
	 * a RuntimException is thrown. 
	 * 
	 * @param calibrListener
	 * @see #process()
	 */
	public void processAsync(CalibrationListener calibrListener) {
		if (thread == null) {
			this.calibrListener = calibrListener;
			thread = new Thread(this);
			thread.start();
		}
		else {
			throw new RuntimeException("The calibration process is already running.");
		}
	}

	@Override
	public void run() {
		synchronized (intrinsicLock) {
			process();
		}
		// callback if needed
		if (calibrListener != null) {
			calibrListener.onCalibrationCompleted(intrinsic);
		}
		thread = null;
	}	
}
