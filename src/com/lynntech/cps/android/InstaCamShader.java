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

import java.util.HashMap;

import android.opengl.GLES20;
import android.util.Log;

/**
 * Helper class for handling shaders.
 */
public final class InstaCamShader {

	// Shader program handles.
	private int mProgram = 0;
	private int mShaderFragment = 0;
	// HashMap for storing uniform/attribute handles.
	private final HashMap<String, Integer> mShaderHandleMap = new HashMap<String, Integer>();
	private int mShaderVertex = 0;

	/**
	 * Deletes program and shaders associated with it.
	 */
	public void deleteProgram() {
		GLES20.glDeleteShader(mShaderFragment);
		GLES20.glDeleteShader(mShaderVertex);
		GLES20.glDeleteProgram(mProgram);
		mProgram = mShaderVertex = mShaderFragment = 0;
	}

	/**
	 * Get id for given handle name. This method checks for both attribute and
	 * uniform handles.
	 * 
	 * @param name	Name of handle.
	 * @return Id for given handle or -1 if none found.
	 */
	public int getHandle(String name) {
		if (mShaderHandleMap.containsKey(name)) {
			return mShaderHandleMap.get(name);
		}
		int handle = GLES20.glGetAttribLocation(mProgram, name);
		if (handle == -1) {
			handle = GLES20.glGetUniformLocation(mProgram, name);
		}
		if (handle == -1) {
			// One should never leave log messages but am not going to follow
			// this rule. This line comes handy if you see repeating 'not found'
			// messages on LogCat - usually for typos otherwise annoying to
			// spot from shader code.
			Log.d("GlslShader", "Could not get attrib location for " + name);
		} else {
			mShaderHandleMap.put(name, handle);
		}
		return handle;
	}

	/**
	 * Get array of ids with given names. Returned array is sized to given
	 * amount name elements.
	 * 
	 * @param names		List of handle names.
	 * @return An array of handle ids.
	 */
	public int[] getHandles(String... names) {
		int[] res = new int[names.length];
		for (int i = 0; i < names.length; ++i) {
			res[i] = getHandle(names[i]);
		}
		return res;
	}

	/**
	 * Helper method for compiling a shader.
	 * 
	 * @param shaderType
	 *            Type of shader to compile
	 * @param source
	 *            String presentation for shader
	 * @return id for compiled shader
	 */
	private int loadShader(int shaderType, String source) throws Exception {
		int shader = GLES20.glCreateShader(shaderType);
		if (shader != 0) {
			GLES20.glShaderSource(shader, source);
			GLES20.glCompileShader(shader);
			int[] compiled = new int[1];
			GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
			if (compiled[0] == 0) {
				String error = GLES20.glGetShaderInfoLog(shader);
				GLES20.glDeleteShader(shader);
				throw new Exception(error);
			}
		}
		return shader;
	}

	/**
	 * Compiles vertex and fragment shaders and links them into a program one
	 * can use for rendering. Once OpenGL context is lost and onSurfaceCreated
	 * is called, there is no need to reset existing GlslShader objects but one
	 * can simply reload shader.
	 * 
	 * @param vertexSource
	 *            String presentation for vertex shader
	 * @param fragmentSource
	 *            String presentation for fragment shader
	 */
	public void setProgram(String vertexSource, String fragmentSource)
			throws Exception {
		mShaderVertex = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
		mShaderFragment = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
		int program = GLES20.glCreateProgram();
		if (program != 0) {
			GLES20.glAttachShader(program, mShaderVertex);
			GLES20.glAttachShader(program, mShaderFragment);
			GLES20.glLinkProgram(program);
			int[] linkStatus = new int[1];
			GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
			if (linkStatus[0] != GLES20.GL_TRUE) {
				String error = GLES20.glGetProgramInfoLog(program);
				deleteProgram();
				throw new Exception(error);
			}
		}
		mProgram = program;
		mShaderHandleMap.clear();
	}

	/**
	 * Activates this shader program.
	 */
	public void useProgram() {
		GLES20.glUseProgram(mProgram);
	}

}
