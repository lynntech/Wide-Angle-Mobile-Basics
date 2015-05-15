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

import boofcv.struct.calib.IntrinsicParameters;

/**
 * Interface to listen for calibration completed.
 * This interface is used to implement the callback pattern to receive notification that
 * the calibration process terminated.
 * 
 * @author Christian Bruccoleri
 * @see boofcv.struct.calib.IntrinsicParameters
 */
public interface CalibrationListener {

	/**
	 * Called when an asynchronous calibration is completed.
	 * @param intrinsic Contains the intrinsic calibration parameters.
	 */
	public void onCalibrationCompleted(IntrinsicParameters intrinsic);
}
