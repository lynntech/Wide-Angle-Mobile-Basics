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

/*
 * Copyright (c) 2011-2014, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.lynntech.cps.android.calibration;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Rect;
import boofcv.alg.distort.ImageDistort;
import boofcv.alg.distort.LensDistortionOps;
import boofcv.alg.misc.GImageMiscOps;
import boofcv.android.ConvertBitmap;
import boofcv.android.ConvertNV21;
import boofcv.android.VisualizeImageData;
import boofcv.struct.calib.IntrinsicParameters;
import boofcv.struct.image.ImageFloat32;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.ImageUInt8;
import boofcv.core.image.ConvertImage;
import boofcv.core.image.border.BorderType;
import java.util.List;

import com.lynntech.cps.android.R;

/**
 * This Activity was based on the BoofCV example that captures and displays a video stream. This activity now
 * has the ability to perform focus on touch, to collect images used for camera calibration and to perform the calibration.
 * The original comment that appeared with the example is reported below, and it is still applicable.
 * 
 * @author Christian Bruccoleri
 * 
 * Demonstration of how to process a video stream on an Android device using BoofCV.  Most of the code below
 * deals with handling Android and all of its quirks.  Video streams can be accessed in Android by processing
 * a camera preview.  Data from a camera preview comes in an NV21 image format, which needs to be converted.
 * After it has been converted it needs to be processed and then displayed.  Note that several locks are required
 * to avoid the three threads (GUI, camera preview, and processing) from interfering with each other.
 *
 * @author Peter Abeles
 */
public class CalibrationActivity extends Activity 
                                 implements Camera.PreviewCallback, CalibrationListener, AutoFocusCallback 
{
	/// Camera object used to capture images.
	private Camera mCamera;
	
	/// View used to interact with user.
	private Visualization mDraw;
	
	/// View used to show images (i.e. camera preview indeed).
	private CameraPreview mPreview;

	// Two images are needed to store the converted preview image to prevent a thread conflict from occurring
	private ImageUInt8 gray1, gray2;

	/// Object used to store the image after distortion has been removed. 
	private ImageFloat32 grayf32_undist;
	
	/// Android image data used for displaying the results
	private Bitmap output;
	
	// temporary storage that's needed when converting from BoofCV to Android image data types
	private byte[] storage;

	// Thread where image data is processed
	private ThreadProcess thread;

	// Object used for synchronizing gray images
	private final Object lockGray = new Object();
	
	// Object used for synchronizing output image
	private final Object lockOutput = new Object();
	
	// Object used for synchronizing snap picture requests
	private final Object lockSnap = new Object();

	/// Flag used to signal that the user requested a calibration snap.
	private volatile boolean snapRequest;
	
	// if true the input image is flipped horizontally
	// Front facing cameras need to be flipped to appear correctly
	private boolean flipHorizontal;
	
	/// Counter of the number of pictures snapped for calibration.
	private int snapCount;
	
	/// Calibration object instance.
	private CalibrateMonocularPlanar calib;
	
	/// Reference to button Calibrate
	private ImageButton btnCalibrate;
	
	/// Reference to button Snap
	private ImageButton btnSnap;
	
	/// Camera intrinsic parameters (after calibration)
	private IntrinsicParameters intrinsic;
	
	/// Object used to apply un-distortion to images
	private ImageDistort<ImageFloat32, ImageFloat32> undistAllInside;

	/// Object to hold a reference to the UI text view that reports the number of pictures captured.
	private TextView txtSnapCount;
	
	/// A progress dialog to show calibration progress.
	private ProgressDialog progDialog;
	
	/// This object is used to provide haptic feedback to the user.
	private Vibrator vibrator;
	
	/// Counts the number of times Autofocus has been attempted.
	private int autoFocusRetryCount;
	
	/// Size of the area used for Autofocus by touch.
	private float focusAreaSize;

	/// Object used to control autofocus.
	private FocusController focusController;
	
	/// Get the size of the Autofocus area (square side)
	public float getFocusAreaSize() {
		return focusAreaSize;
	}

	
	/// Set the size of the autofocus area (i.e. the side of a square area).
	public void setFocusAreaSize(float focusAreaSize) {
		this.focusAreaSize = focusAreaSize;
	}


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.video);
		
		// Create the object to perform the calibration
		calib = new CalibrateMonocularPlanar();

		// Used to visualize the results
		mDraw = new Visualization(this);

		// Create our Preview view and set it as the content of our activity.
		mPreview = new CameraPreview(this,this,true);

		FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);

		preview.addView(mPreview);
		preview.addView(mDraw);
		
		btnCalibrate = (ImageButton)findViewById(R.id.btnCalibrate);
		btnCalibrate.setEnabled(false);	
		btnSnap = (ImageButton)findViewById(R.id.btn_snap);
		txtSnapCount = (TextView)findViewById(R.id.txtSnapCount);
		
		// Prepare the progress dialog used for calibration
		progDialog = new ProgressDialog(this);
		progDialog.setMessage("Calibrating..");
		progDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
	    progDialog.setIndeterminate(true);		
		
	    // reset the number of pictures acquired
		snapCount = 0;
		
		// respond to user's tap with focusing
		mDraw.setOnTouchListener(new View.OnTouchListener() {
	        @Override
	        public boolean onTouch(View v, MotionEvent event) {
	            if (event.getAction() == MotionEvent.ACTION_DOWN) {
	                focusOnTouch(event);
	            }
	            // v.performClick() is called to keep the linter happy..
	            return v.performClick();
	        }
	    });
		vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
		focusAreaSize = 250f;
		autoFocusRetryCount = 0;
	}

	
	/**
	 * Show a {@Link android.widget.Toast} with a specific message.
	 * A toast is a small text area that appears over the window client area. It is handled by Android and
	 * it is supposed to convey a short message for notification purposes.
	 * 
	 * @param textToDisplay		The message to be displayed.
	 * @param duration			This can be either <code>Toast.LENGTH_LONG</code> or <code>Toast.LENGTH_SHORT</code>.
	 */
	public void textToast(String textToDisplay, int duration) {
		if (duration < 0) {
			duration = Toast.LENGTH_LONG;
		}
		Context context = getApplicationContext();
		CharSequence text = textToDisplay;
		Toast toast = Toast.makeText(context, text, duration);
		toast.setGravity(Gravity.TOP|Gravity.START, 50, 50);
		toast.show();
	}
	
	
	/**
	 * The Resume state is part of the Android Activity life cycle.
	 */
	@Override
	protected void onResume() {
		super.onResume();
		setUpAndConfigureCamera();
		resetUI();
	}

	
	/**
	 * The Pause state is part of the Android Activity life cycle.
	 */
	@Override
	protected void onPause() {
		super.onPause();

		// stop the camera preview and all processing
		if (mCamera != null){
			mPreview.setCamera(null);
			focusController = null;
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
			thread.stopThread();
			thread = null;
		}
	}
	
	
	/** 
	 * Vibrate the device for one-half seconds (500ms).
	 */
	protected void vibrate() {
		this.vibrate(500);
	}

	
	/**
	 * Vibrate the device for the specified duration.
	 * @param duration Duration of vibration in milliseconds (between 0 and 4000). If outside bounds, duration is set to 500ms.
	 * @see #vibrate()
	 */
	protected void vibrate(int duration) {
		if (duration < 0 || duration > 4000) {
			duration = 500;
		}
		if (vibrator == null) {
			vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
		}
		vibrator.vibrate(duration);
	}
	
	
	/**
	 * Set the #snapRequest flag. 
	 * @param val If true requests to capture the next available picture from the video sequence, otherwise stops capture.
	 */
	private void setSnapRequest(boolean val) {
		synchronized(lockSnap) {
			snapRequest = val;
		}
	}
	
	
	/**
	 * Get the the value of the {@link #snapRequest} flag.
	 * @return The value of the {@link #snapRequest} flag.
	 */
	private boolean getSnapRequest() {
		boolean res;
		synchronized(lockSnap) {
			res = snapRequest;
		}
		return res;
	}

	
	/**
	 * Sets up the camera if it is not already setup. Since the device may have more than one camera, this
	 * selects the back camera and then configures it. The preview area is set to the closest available 
	 * resolution as requested by the caller {@link Activity} through the {@link Intent} or to 720 x 480. 
	 * A compromise between resolution and speed should be expected.
	 * 
	 * The function also sets up the Autofocus mode and prepare the camera for the use of focus on specific 
	 * area regions. A minimum amount of diagnostics is performed to ensure that the state of the camera is 
	 * kept consistent with the device capabilities. Warning and debugging information is sent to the Log.
	 * 
	 * Finally the image processing {@link #thread} is started, providing output on the preview object {@link #mPreview}.
	 */
	private void setUpAndConfigureCamera() {
		int desiredWidth, desiredHeight;
		// Open and configure the camera
		mCamera = selectAndOpenCamera();
		Camera.Parameters param = mCamera.getParameters();

		// Select the preview size closest to 720x480
		// Smaller images are recommended because some computer vision operations are very expensive
		List<Camera.Size> sizes = param.getSupportedPreviewSizes();
		Intent intent = getIntent();
		
		// determine preview size
		if (intent.getAction().equals("com.lynntech.cps.android.calibration.CALIBRATE")) {
			// assume that the caller communicated what size the preview should be
			Bundle extra = intent.getExtras();
			desiredWidth = extra.getInt("width");
			desiredHeight = extra.getInt("height");
		}
		else {
			// choose default values
			desiredWidth = 720;
			desiredHeight= 480;
		}
		Camera.Size s = sizes.get(closest(sizes, desiredWidth, desiredHeight));
		param.setPreviewSize(s.width,s.height);
		// Setup Autofocus parameters
		int numFocusAreas = param.getMaxNumFocusAreas();
		Log.i("FOCUS", String.format("Max focus areas: %1d", numFocusAreas));
		// Get the current setting of Focus Areas
		List<Camera.Area> areas = param.getFocusAreas();
		String focusAreasDescr;
		if (areas == null) {
			focusAreasDescr = "Assigned by camera driver.";
		}
		else {
			focusAreasDescr = areas.toString();
		}
		// Get supported focus modes
		List<String> focusModes = mCamera.getParameters().getSupportedFocusModes();
		boolean isAutoSupported = false, isMacroSupported = false;
		for (String mode: focusModes) {
			Log.i("FOCUSMODES", mode);
			if (mode.equals("auto")) {
				isAutoSupported = true;
			}
			else if (mode.equals("macro")) {
				isMacroSupported = true;
			}
		}
		if (!isAutoSupported) {
			Log.w("AUTOFOCUS", "AUTO mode not supported.");
		}
		else {
			// Set camera focus mode to Auto
			param.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); 
			mCamera.setParameters(param);
			// Provide feedback of the current autofocus mode.
			textToast("Autofocus mode: " + param.getFocusMode() + "\n" 
					+ "# Focus areas: " + numFocusAreas + "\n"
					+ "Focus area: " + focusAreasDescr,
					Toast.LENGTH_LONG);			
		}
		if (! isMacroSupported) {
			Log.w("AUTOFOCUS", "Macro mode is not supported.");
		}
		// Setup the focus controller
		focusController = new FocusController(mCamera, vibrator);

		// declare image data
		gray1 = new ImageUInt8(s.width,s.height);
		gray2 = new ImageUInt8(s.width,s.height);
		//derivX = new ImageSInt16(s.width,s.height);
		//derivY = new ImageSInt16(s.width,s.height);
		output = Bitmap.createBitmap(s.width,s.height,Bitmap.Config.ARGB_8888 );
		storage = ConvertBitmap.declareStorage(output, storage);

		// start image processing thread
		thread = new ThreadProcess();
		thread.start();

		// Start the video feed by passing it to mPreview
		mPreview.setCamera(mCamera);
	}

	
	/**
	 * Step through the camera list and select a camera.  It is also possible that there is no camera.
	 * The camera hardware requirement in AndroidManifest.xml was turned off so that devices with just
	 * a front facing camera can be found.  Newer SDK's handle this in a more sane way, but with older devices
	 * you need this work around.
	 */
	private Camera selectAndOpenCamera() {
		Camera.CameraInfo info = new Camera.CameraInfo();
		int numberOfCameras = Camera.getNumberOfCameras();

		int selected = -1;

		for (int i = 0; i < numberOfCameras; i++) {
			Camera.getCameraInfo(i, info);

			if( info.facing == Camera.CameraInfo.CAMERA_FACING_BACK ) {
				selected = i;
				flipHorizontal = false;
				break;
			} else {
				// default to a front facing camera if a back facing one can't be found
				selected = i;
				flipHorizontal = true;
			}
		}

		if( selected == -1 ) {
			dialogNoCamera();
			return null; // won't ever be called
		} else {
			return Camera.open(selected);
		}
	}

	
	/**
	 * Gracefully handle the situation where a camera could not be found.
	 */
	private void dialogNoCamera() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Your device has no cameras!")
				.setCancelable(false)
				.setPositiveButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						System.exit(0);
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	
	/**
	 * Goes through the size list and selects the one which is the closest specified size.
	 */
	public static int closest( List<Camera.Size> sizes , int width , int height ) {
		int best = -1;
		int bestScore = Integer.MAX_VALUE;

		for( int i = 0; i < sizes.size(); i++ ) {
			Camera.Size s = sizes.get(i);
			// show supported resolutions
			//System.out.printf("Res: %d x %d\n", s.width, s.height);
			int dx = s.width-width;
			int dy = s.height-height;
			
			int score = dx*dx + dy*dy;
			if( score < bestScore ) {
				best = i;
				bestScore = score;
			}
		}

		return best;
	}

	
	/**
	 * Called each time a new image arrives in the data stream.
	 * 
	 * Can only do trivial amounts of image processing inside this function or else the system may 
	 * become unresponsive and lock. To work around this issue most of the processing has been 
	 * pushed onto a {@link #thread}.
	 * 
	 * The only processing consists in converting the image data from the Android native format NV21 
	 * to a grayscale image that the rest of the app can handle.
	 * 
	 * @param bytes		The array containing the image data.
	 * @param camera	The camera object providing the data.
	 */
	@Override
	public void onPreviewFrame(byte[] bytes, Camera camera) {

		// convert from NV21 format into gray scale
		synchronized (lockGray) {
			ConvertNV21.nv21ToGray(bytes,gray1.width,gray1.height,gray1);
		}
		// wake-up the worker thread
		thread.interrupt();
	}
	
	
	/**
	 * Fired when the user presses the Snap button.
	 * @param view The button being pressed.
	 */
	public void btnSnapOnClick(View view) {
		snapCount ++;
		txtSnapCount.setText(String.format("%d", snapCount));
		setSnapRequest(true);
		if (snapCount >= 15) {
			btnCalibrate.setEnabled(true);
		}
	}
	
	
	/**
	 * Fired when the Calibrate button is pressed.
	 * @param view The button being pressed.
	 */
	public void btnCalibrateOnClick(View view) {
		setEnableButtons(false);
		textToast("Calibrating, please wait...", Toast.LENGTH_LONG);
		progDialog.show();
		calib.processAsync(this);
	}
	
	
	/**
	 * Enable or disable (greyed out) buttons on the user interface.
	 * This function is called to disable buttons while calibration is being done and
	 * then to enable when it is complete.
	 * @param value		{@code true} enables the buttons and {@code false} disables them.
	 */
	private void setEnableButtons(boolean value) {
		btnCalibrate.setEnabled(value);
		btnCalibrate.invalidate();
		btnSnap.setEnabled(value);
		btnSnap.invalidate();
	}
	
	
	/**
	 * Reset the state of the user interface to the default state. This also clears all the calibration images
	 * thus allowing a fresh start.
	 */
	private void resetUI() {
		// disable calibrate button
		btnCalibrate.setEnabled(false);
		btnSnap.setEnabled(true);
		calib.clear();
		snapCount = 0;
		txtSnapCount.setText(String.format("%d", snapCount));
		synchronized (lockOutput) {
			intrinsic = null;
		}
	}
	
	/**
	 * This method handles Autofocus on a image region specified through touch.
	 * This method is called by {{@link #onTouchEvent(MotionEvent)} on the View that displays the image
	 * i.e. the {@link #mDraw} View.
	 * 
	 * @param event The event that generated the call, used to retrieve the location of the tap.
	 */
	protected void focusOnTouch(MotionEvent event) {
	    if (mCamera != null) { // there is a camera ..
	        Rect focusRect = calculateTapArea(event.getX(), event.getY(), focusAreaSize);
	        mDraw.focusRect = focusRect;
	        mDraw.x = Math.round(event.getX());
	        mDraw.y = Math.round(event.getY());
	        // call the autofocus and register this Activity's onAutoFocus() for the callback
	        //focusController.focusOnRect(focusRect, mDraw.getWidth(), mDraw.getHeight(), this);
	        // use the FocusController implementation of the autofocus callback
	        focusController.focusOnRect(focusRect, mDraw.getWidth(), mDraw.getHeight());
	    }
	}
	
	/**
	 * Convert touch position (x,y) to a box 
	 */
	private Rect calculateTapArea(float x, float y, float focusAreaSize) {
	    int areaSize = Float.valueOf(focusAreaSize).intValue();
	    // ensure it fits in the screen
	    int left = clamp((int) x - areaSize / 2, 0, mDraw.getWidth() - areaSize);
	    int top = clamp((int) y - areaSize / 2, 0, mDraw.getHeight() - areaSize);
	    RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);
	    Log.i("AUTOFOCUS", "X, Y: " + x + ", " + y + " Left: " + left + " TOP: " + top);
	    
	    return new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom));
	}
	
	
	/**
	 * Clamp a value x within min and max.
	 * @param x Input value
	 * @param min Minimum
	 * @param max Maximum
	 * @return The clamped value.
	 */
	private int clamp(int x, int min, int max) {
	    if (x > max) {
	        return max;
	    }
	    if (x < min) {
	        return min;
	    }
	    return x;
	}

	
	@Override
	public void onAutoFocus(boolean success, Camera camera) {
		Log.i("AUTOFOCUS", "Successful? " + success + " Mode: " + mCamera.getParameters().getFocusMode());
		if (success) { // vibrate the phone on successful focus
			this.vibrate();
		}
		if (!success && autoFocusRetryCount < 4) { // Autofocus failed, switch mode and try again
			Parameters params = mCamera.getParameters();
			String focusMode = params.getFocusMode();
			if (focusMode.equals("auto")) {
				Log.i("AUTOFOCUS", "Switching to MACRO");
				params.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
				autoFocusRetryCount += 1;
			}
			else if (focusMode.equals("macro")) {
				Log.i("AUTOFOCUS", "Switching to AUTO");
				params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
				autoFocusRetryCount += 1;
			}
			else { // just in case is another mode
				autoFocusRetryCount += 1;
			}
			mCamera.setParameters(params);
			mCamera.autoFocus(this);
		}
		else { // reset retry-count
			autoFocusRetryCount = 0;
		}
	}

	
	/**
	 * Draws on top of the video stream for visualizing computer vision results.
	 */
	private class Visualization extends SurfaceView {

		@SuppressWarnings("unused")
		Activity activity;
		
		/**
		 * The area used by Autofocus algorithm. This must be given in image coordinates.
		 */
		Rect focusRect;

		/**
		 * Location along image columns of the center of the focus area.
		 */
		int x;
		
		/**
		 * Location along image rows of the center of the focus area.
		 */
		int y;
		
		/**
		 * Paint object used to show the focus area
		 */
		Paint focusPaint;

		/**
		 * Constructor
		 * @param context	The parent activity.
		 */
		public Visualization(Activity context) {
			super(context);
			this.activity = context;

			// This call is necessary, or else the draw method will not be called.
			setWillNotDraw(false);
			
			// Color used to paint focus area
			focusRect = null;
			focusPaint = new Paint();
			focusPaint.setColor(Color.RED);
			focusPaint.setStyle(Paint.Style.STROKE);
		}

		/**
		 * Scale and display the image {@link CalibrationActivity#output}.
		 * It also shows a red rectangle and its center as an overlay to mark the area used by Autofocus.
		 */
		@Override
		protected void onDraw(Canvas canvas){

			synchronized ( lockOutput ) {
				int w = canvas.getWidth();
				int h = canvas.getHeight();

				// fill the window and center it
				double scaleX = w/(double)output.getWidth();
				double scaleY = h/(double)output.getHeight();

				double scale = Math.min(scaleX,scaleY);
				double tranX = (w-scale*output.getWidth())/2;
				double tranY = (h-scale*output.getHeight())/2;

				// save the transformation matrix
				canvas.save();
				canvas.translate((float)tranX,(float)tranY);
				canvas.scale((float)scale,(float)scale);
				// draw the image
				canvas.drawBitmap(output,0,0,null);
				// restore the transformation matrix
				canvas.restore();
			}
			if (focusRect != null) { // show the area being focused
				canvas.drawRect(focusRect, focusPaint);
				canvas.drawCircle(x,  y, 20, focusPaint);
			}
		}
		
		/**
		 * Does nothing special, just required to make sure that touch events act as expected.
		 */
		@Override
		public boolean performClick() {
			return super.performClick();
		}
		
	} // inner class Visualization


	/**
	 * This function is called when BoofCV completes asynchronous calibration.
	 * 
	 * This is a callback function used by BoofCV to notify the user program that the asynchronous calibration task has been completed.
	 * This function starts a {@link android#widget#Toast} to inform the user of the calibration result.
	 * A new image buffer, to hold the undistorted image is initialized and the intrinsic parameters are saved in the
	 * main {@link CalibrationActivity} for later use. The latter operation requires a synchronized region because the worker thread will
	 * try to use the intrinsic parameters as soon as they are available, which can cause a race condition.
	 * 
	 * @param intr 		Calibration results will be stored in this wrapper object. If the object is null the calibration failed.
	 */
	public void onCalibrationCompleted(IntrinsicParameters intr)
	{
		Intent intent = getIntent(); // retrieve the Activity's intent
		Log.i("INTENT", intent.getAction());
		// check if calibration failed
		if (intr == null) {
			if (intent.getAction().equals("com.lynntech.cps.android.calibration.CALIBRATE")) {
				setResult(RESULT_CANCELED, getIntent());
				finish(); // NOTE: this will EXIT the Activity		
			}
			else { // nothing special to do: notify user and continue.
				this.runOnUiThread( new Runnable() {
					public void run() {
						Toast.makeText(CalibrationActivity.this, 
								"Calibration failed; try again.", Toast.LENGTH_LONG).show();
						resetUI();
					}});
				return;
			}
		}
		// Also generate a new image to hold the undistorted parameters, if needed
		if (grayf32_undist == null) {
			grayf32_undist = new ImageFloat32(gray2.width, gray2.height);
		}
		// create a new object to remove distortion from images using the new intrinsic parameters
		undistAllInside = LensDistortionOps.removeDistortion(true, BorderType.VALUE, intr, null, ImageType.single(ImageFloat32.class));
		synchronized (lockOutput) {
			// save the parameters for later use
			this.intrinsic = intr;
		}
		
		// Determine what to do: if Activity was started with an Intent to Calibrate by another app,
		// then provide a result and finish, otherwise notify the user and continue.
		if (intent.getAction().equals("com.lynntech.cps.android.calibration.CALIBRATE")) {
			// started to calibrate: provide result and exit
			intent.putExtras(intrinsicToBundle(intr));
			//intent.setData(Uri.parse("/storage/calibfile.txt"));
			// all went well (I think)
			setResult(RESULT_OK, intent);
			finish(); // NOTE: this will EXIT the Activity
		}
		else {
			// notify the user that the calibration is done
			// Note: this callback is invoked from the worker thread, but the Toast must run in the UI thread.
			this.runOnUiThread( new Runnable() {
				public void run() {
					textToast(
							String.format("Center (x, y): [%f, %f]\nFocal length (fx, fy): [%f, %f]", 
									intrinsic.cx, intrinsic.cy, intrinsic.fx, intrinsic.fy), Toast.LENGTH_LONG);
					setEnableButtons(true);
					progDialog.dismiss();
				}});
		}
	}
	
	
	/**
	 * Packs the Intrinsic parameters from calibration into a {@Bundle} to be returned with an {@link Intent}.
	 * This {@link Activity} saves the result of the calibration in the {@link #intrinsic} property. This function
	 * should be called before returning with the {@link Intent}.
	 * 
	 * @param intr	The intrinsic parameters to be packed.
	 * @return A Bundle object with each value of {@code intr} stored with the key corresponding to the property name of the parameter.
	 * @see #onCalibrationCompleted(IntrinsicParameters)
	 */
	private Bundle intrinsicToBundle(IntrinsicParameters intr) 
	{
		Bundle bundle = new Bundle();
		bundle.putInt("height", intr.getHeight());
		bundle.putInt("width", intr.getWidth());
		bundle.putDouble("fx", intr.getFx());
		bundle.putDouble("fy", intr.getFy());
		bundle.putDouble("skew", intr.getSkew());
		bundle.putDouble("cx", intr.getCx());
		bundle.putDouble("cy", intr.getCy());
		bundle.putDoubleArray("radial", intr.getRadial());
		bundle.putBoolean("flipY", intr.isFlipY());
		return bundle;
	}
	
	
	/**
	 * Worker thread used to do more time consuming image processing.
	 * This thread sleeps until it is interrupted by the Activity thread. When awake, it performs image processing
	 * and displays the image captured by the camera. To display efficiently a simple double-buffering technique 
	 * is used.
	 * If the flag {@link CalibrationActivity#snapRequest} is set to {@code true}, a calibration picture is captured and 
	 * added to the set of pictures to be used for calibration.
	 */
	private class ThreadProcess extends Thread {

		/// {@code true} if a request has been made to stop the thread
		volatile boolean stopRequested = false;
		
		/// {@code true} if the thread is running and can process more data
		volatile boolean running = true;

		/**
		 * Blocks until the thread has stopped
		 */
		public void stopThread() {
			stopRequested = true;
			while( running ) {
				thread.interrupt();
				Thread.yield();
			}
		}

		/**
		 * The main function of the worker thread. Does image capture upon request and displays
		 * the video on the preview window {@Link CalibrationActivity#mPreview}. The latter is invalidated
		 * to cause a redraw event. 
		 * 
		 * In order to prevent deadlocks, this method contains two synchronized
		 * regions to a) perform double buffering, and b) perform data processing on the image buffer.
		 * If the intrinsic calibration parameters are available, i.e. {@link CalibrationActivity#intrinsic} is not null,
		 * the image is "undistorted".
		 *  
		 * "Undistortion" is processor intensive and thus slows down the application, also reducing the frame rate. 
		 */
		@Override
		public void run() {
			while( !stopRequested ) {

				// Sleep until it has been told to wake up
				synchronized ( Thread.currentThread() ) {
					try {
						wait();
					} catch (InterruptedException ignored) {}
				}

				// process the most recently converted image by swapping image buffered
				synchronized (lockGray) {
					ImageUInt8 tmp = gray1;
					gray1 = gray2;
					gray2 = tmp;
				}

				if( flipHorizontal )
					GImageMiscOps.flipHorizontal(gray2);

				if (getSnapRequest()) { // Capture next image and add it to the calibration set
					ImageFloat32 gray2f32 = new ImageFloat32(gray2.width, gray2.height);
					calib.addImage(ConvertImage.convert(gray2, gray2f32));
					setSnapRequest(false);
				}
				
				// render the output in a gray image
				synchronized ( lockOutput ) {
					ImageFloat32 gray2f32 = new ImageFloat32(gray2.width, gray2.height);
					gray2f32 = ConvertImage.convert(gray2, gray2f32);
					if (intrinsic == null) { // before calibration show image as it is
						VisualizeImageData.grayMagnitude(gray2f32, -1, output, null);
					}
					else { // intrinsic != null: show corrected (undistorted) image
						undistAllInside.apply(gray2f32, grayf32_undist);
						VisualizeImageData.grayMagnitude(grayf32_undist, -1, output, null);
					}
				}
				mDraw.postInvalidate();
			}
			running = false;
		}
	} // inner class ThreadProcess

} // class VideoActiviy
