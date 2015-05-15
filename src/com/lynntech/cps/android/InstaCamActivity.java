/*
   Copyright 2015 Lynntech Inc. 
   Contact information:
   - Victor Palmer, 		victor.palmer[\AT]lynntech.com
   - Christian Bruccoleri, 	christian.bruccoleri[\AT]lynntech.com
   
   Research reported in this publication was supported by the National Eye Institute 
   of the National Institutes of Health under Award Number R43EY024800.
   The content is solely the responsibility of the authors and does not necessarily
   represent the official views of the National Institutes of Health.


   This code is based on InstaCam by Harri Smatt. It has been modified from the 
   original version: https://github.com/harism/android_instacam
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

/* Previous copyright notice.

   Copyright 2012 Harri Smatt

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

package com.lynntech.cps.android;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;
import boofcv.struct.calib.IntrinsicParameters;
import com.lynntech.cps.android.R;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.SensorManager;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.lynntech.cps.android.calibration.FocusController;

/**
 * The one and only Activity for this camera application.
 */
@SuppressLint("ClickableViewAccessibility")
public class InstaCamActivity extends Activity {
	private final static String CALIBRATION_FILE_NAME = "camera_calibr.dat";  
	// Custom camera holder class.
	private final InstaCamCamera mCamera = new InstaCamCamera();
	// Common observer for all Buttons.
	private final ButtonObserver mObserverButton = new ButtonObserver();
	// Camera observer for handling picture taking.
	private final CameraObserver mObserverCamera = new CameraObserver();
	// Device orientation observer.
	private OrientationObserver mObserverOrientation;
	// Observer for handling SurfaceTexture creation.
	private final RendererObserver mObserverRenderer = new RendererObserver();
	// Common observer for all SeekBars.
	private final SeekBarObserver mObserverSeekBar = new SeekBarObserver();
	// Common observer for all Spinners.
	private final SpinnerObserver mObserverSpinner = new SpinnerObserver();
	// Application shared preferences instance.
	private SharedPreferences mPreferences;
	// Preview texture renderer class.
	private InstaCamRenderer mRenderer;
	// Shared data instance.
	private final InstaCamData mSharedData = new InstaCamData();
	// Camera Calibration parameters
	private IntrinsicParameters intr;
	// Tool used to control focus
	private FocusController focusController;

	/**
	 * Request code for the calibration Activity.
	 */
	private static final int CALIBRATION_REQUEST = 101;

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		// We prevent screen orientation changes.
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Force full screen view.
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().clearFlags(
				WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);

		// Instantiate device orientation observer.
		mObserverOrientation = new OrientationObserver(this);

		// Instantiate camera handler.
		mCamera.setCameraFront(false);
		mCamera.setSharedData(mSharedData);

		// Set content view.
		setContentView(R.layout.instacam);

		// Set filter spinner adapter.
		Spinner filterSpinner = (Spinner) findViewById(R.id.spinner_filter);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
				this, R.array.filters, R.layout.spinner_text);
		// adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		adapter.setDropDownViewResource(R.layout.spinner_dropdown);
		filterSpinner.setAdapter(adapter);

		// Find renderer view and instantiate it.
		mRenderer = (InstaCamRenderer) findViewById(R.id.instacam_renderer);
		mRenderer.setSharedData(mSharedData);
		mRenderer.setObserver(mObserverRenderer);

		// Hide menu view by default.
		View menu = findViewById(R.id.menu);
		menu.setVisibility(View.GONE);

		// Set Button OnClickListeners.
		findViewById(R.id.button_exit).setOnClickListener(mObserverButton);
		findViewById(R.id.button_shoot).setOnClickListener(mObserverButton);
		findViewById(R.id.button_save).setOnClickListener(mObserverButton);
		findViewById(R.id.button_cancel).setOnClickListener(mObserverButton);
		findViewById(R.id.button_menu).setOnClickListener(mObserverButton);
		findViewById(R.id.button_rotate).setOnClickListener(mObserverButton);
		findViewById(R.id.btn_calibrate).setOnClickListener(mObserverButton);
		
		// Get preferences instance.
		mPreferences = getPreferences(MODE_PRIVATE);

		// Set observer for filter Spinner.
		filterSpinner.setOnItemSelectedListener(mObserverSpinner);
		mSharedData.mFilter = mPreferences.getInt(
				getString(R.string.key_filter), 0);
		if (mSharedData.mFilter == 10 && this.intr== null) {
			// undistort not available
			mSharedData.mFilter = 0;
		}
		filterSpinner.setSelection(mSharedData.mFilter);

		// SeekBar ids as triples { SeekBar id, key id, default value }.
		final int SEEKBAR_IDS[][] = {
				{ R.id.seekbar_brightness, R.string.key_brightness, 5 },
				{ R.id.seekbar_contrast, R.string.key_contrast, 5 },
				{ R.id.seekbar_saturation, R.string.key_saturation, 8 },
				{ R.id.seekbar_corner_radius, R.string.key_corner_radius, 3 } };
		// Set SeekBar OnSeekBarChangeListeners and default progress.
		for (int ids[] : SEEKBAR_IDS) {
			SeekBar seekBar = (SeekBar) findViewById(ids[0]);
			seekBar.setOnSeekBarChangeListener(mObserverSeekBar);
			seekBar.setProgress(mPreferences.getInt(getString(ids[1]), ids[2]));
			// SeekBar.setProgress triggers observer only in case its value
			// changes. And we're relying on this trigger to happen.
			if (seekBar.getProgress() == 0) {
				seekBar.setProgress(1);
				seekBar.setProgress(0);
			}
		}
		intr = null;
		// binds the focus controller to the camera
		//focusController = new FocusController(mCamera.getDeviceCamera());
		mRenderer.setOnTouchListener(new View.OnTouchListener() {
	        @Override
	        public boolean onTouch(View v, MotionEvent event) {
	            if (event.getAction() == MotionEvent.ACTION_DOWN) {
	                focusOnTouch(event);
	            }
	            // v.performClick() is called to keep the linter happy..
	            return v.performClick();
	        }
	    });
		loadSettings();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onPause() {
		super.onPause();
		mCamera.onPause();
		mRenderer.onPause();
		mObserverOrientation.disable();
	}

	@Override
	public void onResume() {
		super.onResume();
		mCamera.onResume();
		mRenderer.onResume();
		focusController = new FocusController(mCamera.getDeviceCamera());
		if (mObserverOrientation.canDetectOrientation()) {
			mObserverOrientation.enable();
		}
	}
	
	
	/**
	 * Get the result from Activities started with an {@link Intent}.
	 * 
	 * The calibration {@link Activity} must be started with {@code startActivityForResult(intent, requestCode)}
	 * for this callback method to be called by the other activity when the function finish() is called within it.
	 */
	public void onActivityResult(int requestCode, int resultCode, Intent data) 
	{
		if (requestCode == CALIBRATION_REQUEST) {
			if (resultCode == RESULT_OK) {
				IntrinsicParameters intr = bundleToIntrinsic(data.getExtras());						
				Log.i("CALIBRATION", "Successful.");
				intr.print();
				// save the result.
				saveSettings(intr);
				this.intr = intr;
				updateRendererCalibrParams();
			}
			else {
				Log.i("CALIBRATION", "Failed.");
			}
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
		final float focusAreaSize = 250f;
	    if (mCamera != null) { // there is a camera ..
	        Size prevSize = mCamera.getDeviceCamera().getParameters().getPreviewSize();
	        Rect focusRect = calculateTapArea(event.getX(), event.getY(), focusAreaSize, prevSize.width, prevSize.height);
	        // call the autofocus and register this Activity's onAutoFocus() for the callback:
	        // focusController.focusOnRect(focusRect, mDraw.getWidth(), mDraw.getHeight(), this);
	        // Use the FocusController implementation of the autofocus callback
	        Log.i("SIZE", String.format("Size: %d x %d",  prevSize.width, prevSize.height));
	        focusController.focusOnRect(focusRect, prevSize.width, prevSize.height);
	        //mCamera.getDeviceCamera().autoFocus(new CameraObserver());
	    }
	}
	
	/**
	 * Convert touch position (x,y) to a box 
	 */
	private Rect calculateTapArea(float x, float y, float focusAreaSize, int width, int height) {
	    int areaSize = Float.valueOf(focusAreaSize).intValue();
	    // ensure it fits in the screen
	    int left = clamp((int) x - areaSize / 2, 0, width - areaSize);
	    int top = clamp((int) y - areaSize / 2, 0, height - areaSize);
	    RectF rectF = new RectF(left, top, left + areaSize, top + areaSize);  
	    return new Rect(Math.round(rectF.left), Math.round(rectF.top), 
	    		Math.round(rectF.right), Math.round(rectF.bottom));
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
	
	/**
	 * Updates the linked renderer with the current calibration parameters.
	 */
	private void updateRendererCalibrParams() {
		mSharedData.imWidth = (float)intr.getWidth();
		mSharedData.imHeight = (float)intr.getHeight();
		mSharedData.setRadial(intr.getRadial());
		mSharedData.setCenter(intr.getCx(), intr.getCy());	
		mSharedData.setSkew((float)intr.getSkew());
	}
	
	
	/**
	 * Save the calibration parameters into the private storage.
	 * If the calibration parameters are {@code null} nothing is saved.
	 * 
	 * Since the internal storage mechanism does not have a good method
	 * to store a {@code double} precision number, the floating point
	 * calibration parameters are converted to {@code long}, which has the same
	 * number of bytes. The inverse conversion must also be done during retrieval. 
	 * 
	 * @param intr 		Calibration parameters to be saved.
	 */
	private void saveSettings(IntrinsicParameters intr)
	{
		if (intr != null) {
			try {
				DataOutputStream bw = new DataOutputStream(
						openFileOutput(CALIBRATION_FILE_NAME, Context.MODE_PRIVATE));
				bw.writeInt(intr.getHeight());
				bw.writeInt(intr.getWidth());
				bw.writeDouble(intr.getFx());
				bw.writeDouble(intr.getFy());
				bw.writeDouble(intr.getSkew());
				bw.writeDouble(intr.getCx());
				bw.writeDouble(intr.getCy());
				double[] radial = intr.getRadial();
				bw.writeInt(radial.length); // how many coefficients?
				for (int i = 0; i < radial.length; i++) {
					bw.writeDouble(radial[i]);
				}
				bw.writeBoolean(intr.isFlipY());
				bw.close();
			}
			catch (FileNotFoundException ex) {
				ex.printStackTrace();
				infoDialog("Could not open the calibration file.");
			}
			catch (IOException ex) {
				ex.printStackTrace();
				infoDialog("Could not write to the calibration file.");
			}
		}
		// else { do nothing }
	}
	
	
	/**
	 * Load calibration parameters from private storage.
	 * 
	 * Create a new set of camera calibration parameters for this {@link Activity}
	 * and load the stored parameters, if present. If no calibration parameters file
	 * is found, the existing intrinsic parameters {@link #intr} are not altered. 
	 */
	private void loadSettings()
	{
		// Implementation details: note that this could have been implemented also
		// with serialization, using ObjectOutputStream, ObjectInputStream.
		// Since I have not tried how well the serialization works with 
		// the class IntrinsicParameters, which is declared as {@code Serializable}, I have
		// chosen a more old fashioned approach. [Christian Bruccoleri]
		File fileCheck = getBaseContext().getFileStreamPath(CALIBRATION_FILE_NAME);
		if (!fileCheck.exists()) {
			Log.w("STORAGE", "Storage file not found: " + CALIBRATION_FILE_NAME);
			// Load default values
			this.intr = new IntrinsicParameters();		
			intr.setHeight(1920);
			intr.setWidth(1280);
			intr.setFx(25);
			intr.setFy(25);
			intr.setSkew(0);
			intr.setCx(0);
			intr.setCy(0);
			double[] radial = new double[2];
			for (int i = 0; i < 2; i++) {
				radial[i] = 0.0;
			}
			intr.setRadial(radial);
			intr.setFlipY(false);
			updateRendererCalibrParams();			
			return;
		}
		try {
			DataInputStream din = new DataInputStream(openFileInput(CALIBRATION_FILE_NAME));
			this.intr = new IntrinsicParameters();		
			intr.setHeight(din.readInt());
			intr.setWidth(din.readInt());
			intr.setFx(din.readDouble());
			intr.setFy(din.readDouble());
			intr.setSkew(din.readDouble());
			intr.setCx(din.readDouble());
			intr.setCy(din.readDouble());
			int numCoeff = din.readInt();
			double[] radial = new double[numCoeff];
			for (int i = 0; i < numCoeff; i++) {
				radial[i] = din.readDouble();
			}
			intr.setRadial(radial);
			intr.setFlipY(din.readBoolean());
			din.close();
			updateRendererCalibrParams();
			// debug
			float[] _radial = mSharedData.getRadial().array();
			float[] _center = mSharedData.getCenter().array();
			Log.i("CALIBRATION", String.format("%.1f x %.1f,  %.4f,%.4f",
					mSharedData.imWidth, mSharedData.imHeight, _radial[0], _radial[1]));
			Log.i("CALIBRATION", String.format("C: (%.1f,  %.1f)", _center[0], _center[1]));
			// end debug
		}
		catch (FileNotFoundException ex) {
			ex.printStackTrace();
			infoDialog("Could not open the calibration file.");
		}
		catch (IOException ex) {
			ex.printStackTrace();
			infoDialog("Could not read the calibration file.");
		}
	}
	
	
	/**
	 * Show an information dialog to the user.
	 */
	private void infoDialog(String msg) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(msg);
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	
	private IntrinsicParameters bundleToIntrinsic(Bundle bundle)
	{
		IntrinsicParameters intr = new IntrinsicParameters();
		intr.setHeight(bundle.getInt("height"));
		intr.setWidth(bundle.getInt("width"));
		intr.setFx(bundle.getDouble("fx"));
		intr.setFy(bundle.getDouble("fy"));
		intr.setSkew(bundle.getDouble("skew"));
		intr.setCx(bundle.getDouble("cx"));
		intr.setCy(bundle.getDouble("cy"));
		intr.setRadial(bundle.getDoubleArray("radial"));
		intr.setFlipY(bundle.getBoolean("flipY"));
		return intr;
	}

	
	private final void setCameraFront(final boolean front) {
		View button = findViewById(R.id.button_rotate);

		PropertyValuesHolder holderRotation = PropertyValuesHolder.ofFloat(
				"rotation", button.getRotation(), 360);
		ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(button,
				holderRotation).setDuration(700);
		anim.addListener(new Animator.AnimatorListener() {
			@Override
			public void onAnimationCancel(Animator animation) {
			}

			@Override
			public void onAnimationEnd(Animator animation) {
				findViewById(R.id.button_rotate).setRotation(0);
				mCamera.setCameraFront(front);
			}

			@Override
			public void onAnimationRepeat(Animator animation) {
			}

			@Override
			public void onAnimationStart(Animator animation) {
			}
		});
		anim.start();
	}

	
	private final void setMenuVisible(boolean visible) {
		View menu = findViewById(R.id.menu);
		View button = findViewById(R.id.button_menu);

		if (visible) {
			// Animate menu visible
			menu.setPivotY(0);
			menu.setVisibility(View.VISIBLE);
			PropertyValuesHolder holderAlpha = PropertyValuesHolder.ofFloat(
					"alpha", menu.getAlpha(), 1);
			PropertyValuesHolder holderScale = PropertyValuesHolder.ofFloat(
					"scaleY", menu.getScaleY(), 1);
			ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(menu,
					holderAlpha, holderScale).setDuration(500);
			anim.start();

			// Animate menu button "upside down"
			holderScale = PropertyValuesHolder.ofFloat("scaleY",
					button.getScaleY(), -1);
			anim = ObjectAnimator.ofPropertyValuesHolder(button, holderScale)
					.setDuration(500);
			anim.start();
		} else {
			// Hide menu
			menu.setPivotY(0);
			PropertyValuesHolder holderAlpha = PropertyValuesHolder.ofFloat(
					"alpha", menu.getAlpha(), 0);
			PropertyValuesHolder holderScale = PropertyValuesHolder.ofFloat(
					"scaleY", menu.getScaleY(), 0);
			ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(menu,
					holderAlpha, holderScale).setDuration(500);
			anim.addListener(new Animator.AnimatorListener() {
				@Override
				public void onAnimationCancel(Animator animation) {
				}

				@Override
				public void onAnimationEnd(Animator animation) {
					findViewById(R.id.menu).setVisibility(View.GONE);
				}

				@Override
				public void onAnimationRepeat(Animator animation) {
				}

				@Override
				public void onAnimationStart(Animator animation) {
				}
			});
			anim.start();

			// Animate menu button back to "normal"
			holderScale = PropertyValuesHolder.ofFloat("scaleY",
					button.getScaleY(), 1);
			anim = ObjectAnimator.ofPropertyValuesHolder(button, holderScale)
					.setDuration(500);
			anim.start();
		}
	}

	private final class ButtonObserver implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			// On exit simply close Activity.
			case R.id.button_exit:
				finish();
				break;
			// On shoot trigger picture taking.
			case R.id.button_shoot:
				// We do not want to receive orientation changes until picture
				// is either saved or cancelled.
				mObserverOrientation.disable();
				mCamera.takePicture(mObserverCamera);
				break;
			// Pressing menu button switches menu visibility.
			case R.id.button_menu:
				View view = findViewById(R.id.menu);
				setMenuVisible(view.getVisibility() != View.VISIBLE);
				break;
			// Save button starts separate thread for saving picture.
			case R.id.button_save:
				// Disabled
				// TODO: alert user
				break;
			// Cancel button simply discards current picture data.
			case R.id.button_cancel:
				mSharedData.mImageData = null;
				findViewById(R.id.buttons_shoot).setVisibility(View.VISIBLE);
				findViewById(R.id.buttons_cancel_save).setVisibility(View.GONE);
				mCamera.startPreview();
				// Re-enable orientation observer.
				mObserverOrientation.enable();
				break;
			case R.id.button_rotate:
				setCameraFront(!mCamera.isCameraFront());
				break;
			case R.id.btn_calibrate:
				// Start the calibration activity
				final String action = "com.lynntech.cps.android.calibration.CALIBRATE"; 
				Intent intent = new Intent();
				Size size = mCamera.getDeviceCamera().getParameters().getPreviewSize();
				// try to do calibration for this specific preview size
				Log.d("SIZE (intent): ", "Size: " + size.width + " x " + size.height);
				Bundle extras = new Bundle();
				extras.putInt("width", size.width);
				extras.putInt("height", size.height);
				intent.putExtras(extras);
				intent.setAction(action);
				startActivityForResult(intent, InstaCamActivity.CALIBRATION_REQUEST);
				break;
			default:
				Log.i("BUTTON", "ButtonObserver - Not handled View id: " + v.getId());
			}
		}
	}

	/**
	 * Class for implementing Camera related callbacks.
	 */
	private final class CameraObserver implements InstaCamCamera.Observer, AutoFocusCallback {
		@Override
		public void onAutoFocus(boolean success) {
			// If auto focus failed show brief notification about it.
			if (!success) {
				Toast.makeText(InstaCamActivity.this, R.string.focus_failed,
						Toast.LENGTH_SHORT).show();
			}
		}

		@Override
		public void onPictureTaken(byte[] data) {
			// Once picture is taken just store its data.
			mSharedData.mImageData = data;
			// And time it was taken.
			Calendar calendar = Calendar.getInstance();
			mSharedData.mImageTime = calendar.getTimeInMillis();
		}

		@Override
		public void onShutter() {
			// At the point picture is actually taken switch footer buttons.
			findViewById(R.id.buttons_cancel_save).setVisibility(View.VISIBLE);
			findViewById(R.id.buttons_shoot).setVisibility(View.GONE);
		}

		@Override
		public void onAutoFocus(boolean success, Camera camera) {
			this.onAutoFocus(success);
		}
	}

	/**
	 * Class for observing device orientation.
	 */
	private class OrientationObserver extends OrientationEventListener {

		public OrientationObserver(Context context) {
			super(context, SensorManager.SENSOR_DELAY_NORMAL);
			disable();
		}

		@Override
		public void onOrientationChanged(int orientation) {
			orientation = (((orientation + 45) / 90) * 90) % 360;
			if (orientation != mSharedData.mOrientationDevice) {

				// Prevent 270 degree turns.
				int original = mSharedData.mOrientationDevice;
				if (Math.abs(orientation - original) > 180) {
					if (orientation > original) {
						original += 360;
					} else {
						original -= 360;
					}
				}

				// Trigger rotation animation.
				View shoot = findViewById(R.id.button_shoot);
				PropertyValuesHolder holderRotation = PropertyValuesHolder
						.ofFloat("rotation", -original, -orientation);
				ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(
						shoot, holderRotation).setDuration(500);
				anim.start();

				// Store and calculate new orientation values.
				mSharedData.mOrientationDevice = orientation;
				mCamera.updateRotation();
			}
		}

	}

	/**
	 * Class for implementing InstaCamRenderer related callbacks.
	 */
	private class RendererObserver implements InstaCamRenderer.Observer {
		@Override
		public void onSurfaceTextureCreated(SurfaceTexture surfaceTexture) {
			// Once we have SurfaceTexture try setting it to Camera.
			try {
				mCamera.stopPreview();
				mCamera.setPreviewTexture(surfaceTexture);

				// Start preview only if shoot -button is visible. Otherwise we
				// do have image captured for later use.
				if (findViewById(R.id.buttons_shoot).getVisibility() == View.VISIBLE)
					mCamera.startPreview();

			} catch (final Exception ex) {
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(InstaCamActivity.this, ex.getMessage(),
								Toast.LENGTH_LONG).show();
					}
				});
			}
		}
	}

	/**
	 * Class for implementing SeekBar related callbacks.
	 */
	private final class SeekBarObserver implements
			SeekBar.OnSeekBarChangeListener {

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {

			switch (seekBar.getId()) {
			// On brightness recalculate shared value and update preferences.
			case R.id.seekbar_brightness: {
				mPreferences.edit()
						.putInt(getString(R.string.key_brightness), progress)
						.commit();
				mSharedData.mBrightness = (progress - 5) / 10f;

				TextView textView = (TextView) findViewById(R.id.text_brightness);
				textView.setText(getString(R.string.seekbar_brightness,
						progress - 5));
				break;
			}
			// On contrast recalculate shared value and update preferences.
			case R.id.seekbar_contrast: {
				mPreferences.edit()
						.putInt(getString(R.string.key_contrast), progress)
						.commit();
				mSharedData.mContrast = (progress - 5) / 10f;
				TextView textView = (TextView) findViewById(R.id.text_contrast);
				textView.setText(getString(R.string.seekbar_contrast,
						progress - 5));
				break;
			}
			// On saturation recalculate shared value and update preferences.
			case R.id.seekbar_saturation: {
				mPreferences.edit()
						.putInt(getString(R.string.key_saturation), progress)
						.commit();
				mSharedData.mSaturation = (progress - 5) / 10f;
				TextView textView = (TextView) findViewById(R.id.text_saturation);
				textView.setText(getString(R.string.seekbar_saturation,
						progress - 5));
				break;
			}
			// On radius recalculate shared value and update preferences.
			case R.id.seekbar_corner_radius: {
				mPreferences
						.edit()
						.putInt(getString(R.string.key_corner_radius), progress)
						.commit();
				mSharedData.mCornerRadius = progress / 10f;
				TextView textView = (TextView) findViewById(R.id.text_corner_radius);
				textView.setText(getString(R.string.seekbar_corner_radius,
						-progress));
				break;
			}
			}
			mRenderer.requestRender();
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
		}
	}

	/**
	 * Class for implementing Spinner related callbacks.
	 */
	private class SpinnerObserver implements AdapterView.OnItemSelectedListener {
		@Override
		public void onItemSelected(AdapterView<?> parent, View view,
				int position, long id) {
			mPreferences.edit()
					.putInt(getString(R.string.key_filter), position).commit();
			mSharedData.mFilter = position;
			mRenderer.requestRender();
		}

		@Override
		public void onNothingSelected(AdapterView<?> parent) {
		}
	}

}