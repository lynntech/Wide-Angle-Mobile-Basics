Introduction
============

Prometeus is a template to build high performance Computer Vision (CV) applications with OpenGL and the GPU for the Android platform.
We started using InstaCam, by Harri Smatt, as a template for our own applications, but then recognized that while InstaCam 
did what was designed to do very well, however, for our purpose, some changes were necessary.
One of the things that most CV researchers need, at some point or another, is the ability to calibrate the camera. Therefore
we added this ability by making an Android Activity that takes care of camera calibration. This Activity uses another
open source third party library, BoofCV, entirely written in Java. In order to do the calibration you will need a printout
of a checkerboard (a template is included with OpenCV and BoofCV). We wanted to give BoofCV a try and therefore we used for this problem with 
excellent results. BoofCV does not use the GPU however. It also replicates some of the functions available in OpenCV.

One of our goals was being able to use wide field of view lenses on the smartphones; such optics are normally affected by 
radial distortion and calibration can be used to determine the first order distortion coefficients. Once the coefficients
are known they can be used by a fragment shader to perform real time "undistortion" using the GPU.

There is still much work to be done. This platform is just the beginning and a lot of more advanced functions can (and should)
be added to truly support high performance CV on Android.

License
=======

Prometeus, InstaCam, and BoofCV are all released under the Apache 2.0 License, included in the application folder.
There is therefore only one copy of the license. Please refer to the respective copyright holders for additional
information.
It is our intent to give due recognition to all the contributors: if you feel something or somebody was left out, please
let us know and we will fix it.

Acknowledgment
==============

Research reported in this publication was supported by the National Eye Institute 
of the National Institutes of Health under Award Number R43EY024800.
The content is solely the responsibility of the authors and does not necessarily
represent the official views of the National Institutes of Health.

The authors also thank Lynntech, Inc. management for supporting open source development of Prometeus.

Authors
=======

If you want to ask questions or would like to contribute to this project, please drop us an e-mail:
Christian Bruccoleri, christian.bruccoleri_AT_lynntech.com
Victor Palmer, victor.palmer_AT_lynntech.com
