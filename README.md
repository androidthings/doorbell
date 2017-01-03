Android Things Doorbell sample
=====================================

The Android Things Doorbell sample demonstrates how to create a “smart” doorbell.
The sample captures a button press from a user, obtains an image via a camera peripheral,
processes the image data using Google’s Cloud Vision API, and uploads the image, Cloud Vision
annotations and metadata to a Firebase database where it can be viewed by a companion app.


Pre-requisites
--------------

- Android Things compatible board
- Android Things compatible camera (for example, the Raspberry Pi 3 camera module)
- Android Studio 2.2+
- "Google Repository" from the Android SDK Manager
- Google Cloud project with Cloud Vision API enabled
- Firebase database
- The following individual components:
    - 1 push button
    - 1 resistor
    - jumper wires
    - 1 breadboard


Schematics
----------

![Schematics](schematics.png)

Setup and Build
===============

To setup, follow these steps below.

1. Add a valid Google Cloud Vision API key in the constant `CloudVisionUtils.CLOUD_VISION_API_KEY`
 - Create a Google Cloud Platform (GCP) project on [GCP Console](https://console.cloud.google.com/)
 - Enable Cloud Vision API under Library
 - Add an API key under Credentials
 - Copy and paste the Cloud Vision API key to the constant in `CloudVisionUtils.java`

2. Add a valid `google-services.json` from Firebase to `app/` and
   `companionApp/`
 - Create a Firebase project on [Firebase Console](https://console.firebase.google.com)
 - Add an Android app with your specific package name in the project
 - Download the auto-generated `google-services.json` and save to `app/` and `companionApp/` folders

There are two modules: `app` and `companionApp`, the former is on device while the latter on
companion device e.g. Android phone.


Running
=======

To run the `app` module on an Android Things board:

1. Connect a push button to your device's GPIO pin according to the schematics below
2. Deploy and run the `app` module
 - Reboot the Android Things board in order to grant the camera permission (this is a [known
   issue][dp1_release_notes] with Developer Preview 1)
3. Take a picture by pushing the button
4. Verify from Firebase Console that pictures are uploaded to a log in the Firebase database
   of your project
5. Verify from Firebase Console that the uploaded pictures in the log get annotations after
   a small delay from the GCP Cloud Vision

To run the `companionApp` module on your Android phone:

1. Deploy and run the `companionApp` module
2. Verify that you see a new annotated picture everytime you push the button


License
-------

Copyright 2016 The Android Open Source Project, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.

[dp1_release_notes]: https://developer.android.com/things/preview/releases.html#developer_preview_1