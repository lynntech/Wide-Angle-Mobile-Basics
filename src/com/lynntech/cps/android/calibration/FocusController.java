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

package com.lynntech.cps.android.calibration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.os.Vibrator;
import android.util.Log;

/**
 * This Class allows the user to request Autofocus on an area of an image.
 *  
 * The typical use case for this class, within an {@link Activity}, is as follows. Assuming you have a {@link Camera}
 * object defined in your {@code Activity}, like this:
 * 
 * <pre>{@code
 * private Camera camera;
 * }</pre>
 * 
 * Initialize the object in the {@code FocusController} after camera initialization:
 * 
 * <pre>{@code
 * FocusController focusController = new FocusController(camera);
 * }</pre>
 * 
 * You can also pass to the constructor an additional {@link Vibrator} object to make the device vibrate when autofocus succeeds.
 * then call {@link #focusOnRect(Rect, float, float)} when you need the focus. Note that autofocus is asynchronous, thus the function 
 * call will return immediately. Android will use the callback mechanism to provide feedback. {@link FocusController} implements 
 * a callback that already provides some basic responses: output the outcome of the Autofocus ({@code success} flag is true or false)
 * to the system {@code Log}, and retry autofocus up to a specified number of times (see {link {@link #maxAutoFocusRetry}).
 * You may still want to implement your own callback to handle the autofocus event; if that is the case, then provide
 * a {@link #onAutoFocus(boolean, Camera)} callback to the method {@link #focusOnRect(Rect, float, float, AutoFocusCallback)}.
 * 
 * <pre>{@code
 * focusController.focusOnRect(rect, width, height, myAutoFocusCallback);
 * }</pre>
 * 
 * That's it! The driver will take care of the rest (if it can). Ensuring that the driver can actually do autofocus on image areas requires
 * some additional work using {@code Camera.Parameters}. If it cannot, the class will still work, but it will ignore the focus area
 * specified in the autofocus call, e.g. {@link #focusOnRect(Rect, float, float)}.
 * 
 * When attempting autofocus this class will set the autofocus mode to {@code FOCUS_MODE_AUTO} if possible, otherwise to 
 * {@code FOCUS_MODE_MACRO}, and if all fails, it will set the first mode available, as declared by the driver 
 * (there is always at least one if there is a camera in Android).
 * 
 * @author Christian Bruccoleri
 */
public class FocusController implements android.hardware.Camera.AutoFocusCallback {
	
	/** The camera on which autofocus must be performed. */
	protected Camera camera;
	
	/** Maximum autofocus retry. */
	protected int maxAutoFocusRetry;
	
	/** Counter to allow for multiple retries. */
	private int autoFocusRetryCount;
	
	/** Vibration used to provide haptic feedback */
	protected Vibrator vibrator;
	
	/** Flag to detect if focus areas are supported by the camera driver. */
	protected boolean isFocusAreaSupported;
	

	/** @returns {@code true} if the driver supports focus on areas, {@code false} otherwise. */
	public boolean isFocusAreaSupported() {
		return isFocusAreaSupported;
	}


	/** A list of supported autofocus modes populated at object creation. */
	protected List<String> focusModes;
	
	
	public Vibrator getVibrator() {
		return vibrator;
	}


	public void setVibrator(Vibrator vibrator) {
		this.vibrator = vibrator;
	}


	/**
	 * Constructor
	 * @param camera	The camera to be focused, cannot be {@code null}.
	 */
	public FocusController(Camera camera) {
		this(camera, null);
	}

	
	/**
	 * Create a {@code FocusController} with the associated {@code Camera} and {@code Vibrator}.
	 * @param camera	The camera to be focused, cannot be {@code null}.
	 * @param vibrator	Reference to the system {@link Vibrator} to allow for haptic feedback.
	 */
	public FocusController(Camera camera, Vibrator vibrator) {
		if (camera == null) {
			throw new IllegalArgumentException("Camera argument cannot be null.");
		}
		Camera.Parameters params = camera.getParameters();
		this.camera = camera;
		this.autoFocusRetryCount = 0;
		this.vibrator = vibrator;
		this.isFocusAreaSupported = (params.getMaxNumFocusAreas() > 0);
		// get supported focus modes and set a default mode
		List<String> focusModes = params.getSupportedFocusModes();
		if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
			params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		}
		else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_MACRO)) {
			params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		} else { // set the first mode available: there is always at least one (guaranteed by the API documentation)
			params.setFocusMode(focusModes.get(0));
		}
	}
	
	
	/**
	 * Calls autofocus using this object's {@link FocusController#onAutoFocus(boolean, Camera)} as the callback. 
	 * @param focusRect
	 * @param width
	 * @param height
	 * @see {@link #focusOnRect(Rect, float, float, AutoFocusCallback)}
	 */
	public void focusOnRect(Rect focusRect, float width, float height)
	{
		this.focusOnRect(focusRect, width, height, this);
	}
	
	
	/**
	 * Initiate Autofocus on the region specified by the user.
	 * 
	 * @param 	focusRect 	Rectangle of the image to be focused.
	 * @param 	width		Width of the full image in the same units as {@code focusRect}.
	 * @param 	height 		Height of the full image in the same units as {@code focusRect}.
	 * @param 	autoFocusCallback	The function to be called when Autofocus completes.
	 * @see #focusOnRect(Rect, float, float)
	 */
	public void focusOnRect(Rect focusRect, float width, float height, AutoFocusCallback autoFocusCallback) {
        Camera.Parameters parameters = camera.getParameters();
    	if (isFocusAreaSupported) {
	    	camera.cancelAutoFocus();
	        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
	        List<Camera.Area> focusAreas = new ArrayList<Camera.Area>(1);
	        focusAreas.add(rectToArea(focusRect, width, height));
	        parameters.setFocusAreas(focusAreas);
    	}
        camera.setParameters(parameters);
        camera.autoFocus(autoFocusCallback);
	}
	

	/**
	 * Convert a rectangle in pixel into an Area for Autofocus purposes.
	 * @param src A rectangle to be transformed into an Area.
	 */
	private Camera.Area rectToArea(Rect src, float width, float height)
	{
		final float areaSide = 2000f;
	    float leftf = ((float)src.left - width/2.0f) / width * areaSide;
	    float topf = ((float)src.top - height/2.0f) / height * areaSide;	    
	    float widthf = src.width() / width * areaSide;
	    float heightf = src.height() / height * areaSide;
	    RectF rectF = new RectF(leftf, topf, leftf + widthf, topf + heightf);   
		
		return new Camera.Area(
				new Rect(Math.round(rectF.left), Math.round(rectF.top), Math.round(rectF.right), Math.round(rectF.bottom)), 1);
	}
	
	
	/**
	 * This callback method is called when the focusing is completed and should not be called directly.
	 * @see #focusOnRect(Rect)
	 */
	@Override
	public void onAutoFocus(boolean success, Camera camera) {
		Log.i("AUTOFOCUS", "Successful? " + success + " Mode: " + 
				camera.getParameters().getFocusMode().toUpperCase(Locale.getDefault()));
		if (success) { // vibrate the phone on successful focus
			this.vibrate();
		}
		if (!success && autoFocusRetryCount < maxAutoFocusRetry) { // Autofocus failed, switch mode and try again
			Parameters params = camera.getParameters();
			String focusMode = params.getFocusMode();
			if (focusMode.equals("auto")) { // Focus mode is: AUTO, switch to MACRO
				params.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
				autoFocusRetryCount += 1;
			}
			else if (focusMode.equals("macro")) { // Focus mode is: MACRO switch to AUTO
				params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
				autoFocusRetryCount += 1;
			}
			else { // just in case it is another mode
				autoFocusRetryCount += 1;
			}
			camera.setParameters(params);
			camera.autoFocus(this);
		}
		else { // reset retry-count
			autoFocusRetryCount = 0;
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
		if (vibrator != null) {
			vibrator.vibrate(duration);
		}
	}


	public int getMaxAutoFocusRetry() {
		return maxAutoFocusRetry;
	}


	public void setMaxAutoFocusRetry(int maxAutoFocusRetry) {
		if (maxAutoFocusRetry < 1 || maxAutoFocusRetry > 12) {
			throw new IndexOutOfBoundsException("maxAutofocusRetry must be between 1 and 12.");
		}
		this.maxAutoFocusRetry = maxAutoFocusRetry;
	}

}
