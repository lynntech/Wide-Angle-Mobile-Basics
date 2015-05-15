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

/* Previous copyright notice

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

import java.nio.FloatBuffer;

import android.app.ProgressDialog;

/**
 * This class contains data shared across the application.
 * It is used to pass information from the main Activity to the Renderer.
 */
public class InstaCamData {
	// Preview view aspect ration.
	public final float mAspectRatioPreview[] = new float[2];
	// Filter values.
	public float mBrightness, mContrast, mSaturation, mCornerRadius;
	// Predefined filter.
	public int mFilter;
	// Taken picture data (jpeg).
	public byte[] mImageData;
	// Progress dialog while saving picture.
	public ProgressDialog mImageProgress;
	// Picture capture time.
	public long mImageTime;
	// Device orientation degree.
	public int mOrientationDevice;
	// Camera orientation matrix.
	public final float mOrientationM[] = new float[16];

	/// Width of the image in pixel. This is a scale factor for the texture coordinates.
	public float imWidth;
	/// Height of the image in pixel. This is a scale factor for the texture coordinates.
	public float imHeight;
	/// Float Buffer to hold radial distortion parameters
	private FloatBuffer radial4fv;
	/// Backer for radial4fv
	private float[] radialBuf;
	/// Location of the optical axis
	private FloatBuffer center2fv;
	/// Backer for center2fv
	private float[] centerBuf;
	/// Calibration skew parameter
	private float skew;
	
	public float getSkew() {
		return skew;
	}

	public void setSkew(float skew) {
		this.skew = skew;
	}

	/**
	 * Set the radial distortion parameters in a float buffer, ready to be passed to a shader.
	 * Note that this function converts from double to single floating point precision (because
	 * the GPU cannot handle double in most cases, yet).
	 * Note that the coefficients must be scaled to work in Texture coordinates.
	 * 
	 * @param radial	The radial distortion parameters to
	 * @return The {@link FloatBuffer} that was modified, to allow call-chaining.
	 */
	public FloatBuffer setRadial(double[] radial) {
		final int len = radial.length;
//		if (radial.length < len) {
//			throw new IllegalArgumentException(
//					String.format("Parameter 'radial' must have at least %d elements.", len));
//		}
		radialBuf = new float[len];
		for (int i = 0; i < len; i++) {
			radialBuf[i] = (float)radial[i];
		}
		radial4fv = FloatBuffer.wrap(radialBuf);
		// convenience to allow call-"chaining"
		return radial4fv;
	}
	
	/**
	 * Returns the radial distortion parameters in texture coordinates.
	 * @return The radial distortion parameters in GPU format.
	 */
	public FloatBuffer getRadial() {
		return this.radial4fv;
	}
	
	/**
	 * Set the optical axis center calibration parameters for GPU use. 
	 * @param cx	The center along columns, scaled in texture coordinates.
	 * @param cy	The center along rows, scaled in texture coordinates.
	 * @return The {@link FloatBuffer} being set for call-chaining.
	 */
	public FloatBuffer setCenter(double cx, double cy) {
		centerBuf = new float[2];
		centerBuf[0] = (float)cx;
		centerBuf[1] = (float)cy;
		this.center2fv = FloatBuffer.wrap(centerBuf);
		return this.center2fv;
	}
	
	/**
	 * Get the current location of the optical axis center in texture coordinates.
	 * @return A buffer containing the optical axis center in texture coordinates.
	 */
	public FloatBuffer getCenter() {
		return this.center2fv;
	}	
}
