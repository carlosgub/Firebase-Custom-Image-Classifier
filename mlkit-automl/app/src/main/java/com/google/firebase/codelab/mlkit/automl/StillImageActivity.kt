/**
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.codelab.mlkit.automl

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.ml.common.FirebaseMLException
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import kotlinx.android.synthetic.main.activity_still_image.image_preview
import kotlinx.android.synthetic.main.activity_still_image.photo_camera_button
import kotlinx.android.synthetic.main.activity_still_image.photo_library_button
import kotlinx.android.synthetic.main.activity_still_image.result_text
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

class StillImageActivity : BaseActivity() {

  private var currentPhotoFile: File? = null

  private var classifier: ImageClassifier? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Layout del Activity
    setContentView(R.layout.activity_still_image)

    //Boton Tomar foto de la camara
    photo_camera_button.setOnClickListener { takePhoto() }

    //Boton Libreria
    photo_library_button.setOnClickListener { chooseFromLibrary() }

    //Inicializar el Clasificador de Imagenes
    try {
      classifier = ImageClassifier()
    } catch (e: FirebaseMLException) {
      result_text?.text = getString(R.string.fail_to_initialize_img_classifier)
    }
  }

  override fun onDestroy() {
    classifier?.close()
    super.onDestroy()
  }

  //Crear Archivo de la imagen que se va a tomar
  @Throws(IOException::class)
  private fun createImageFile(): File {
    // Crear el nombre del archivo
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())

    // Directorio donde se guardara el archivo
    val storageDir = cacheDir

    //Crear un archivo temporal y guardarlo
    return createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
    ).apply {
      // Cuando se guarde el archivo asignarle el path a currentPhotoFile
      currentPhotoFile = this
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (resultCode != Activity.RESULT_OK) return

    when (requestCode) {
      // Se trajo la imagen de la camara
      REQUEST_IMAGE_CAPTURE -> {
        FirebaseVisionImage.fromFilePath(this, Uri.fromFile(currentPhotoFile)).also {
          classifyImage(it.bitmap)
        }
      }
      // Se trajo la imagen de la galeria
      REQUEST_PHOTO_LIBRARY -> {
        val selectedImageUri = data?.data ?: return
        FirebaseVisionImage.fromFilePath(this, selectedImageUri).also {
          classifyImage(it.bitmap)
        }
      }
    }
  }

  //Funcion para clasificar la imagen
  private fun classifyImage(bitmap: Bitmap) {
    //Verificar que el clasificador no es nulo
    if (classifier == null) {
      result_text?.text = getString(R.string.uninitialized_img_classifier_or_invalid_context)
      return
    }

    // Mostrar la imagen en el ImageView
    image_preview.setImageBitmap(bitmap)

    // Clasificar imagen
    classifier?.classifyFrame(bitmap)?.
      addOnCompleteListener { task ->
        //Si se encontro un parecido
        if (task.isSuccessful) {
          //Mostrar texto
          result_text.text = task.result
        }
        //Si no
        else {
          //Mostrar error
          val e = task.exception
          result_text.text = e?.message
        }
      }
  }

  //Elegir imagen de la libreria
  private fun chooseFromLibrary() {
    val intent = Intent(Intent.ACTION_PICK)
    intent.type = "image/*"

    // Solo imagenes jpeg y png
    val mimeTypes = arrayOf("image/jpeg", "image/png")
    intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)

    startActivityForResult(intent, REQUEST_PHOTO_LIBRARY)
  }

  //Tomar foto
  private fun takePhoto() {
    Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
      takePictureIntent.resolveActivity(packageManager)?.also {
        val photoFile: File? = try {
          createImageFile()
        } catch (e: IOException) {
          Log.e(TAG, "Unable to save image to run classification.", e)
          null
        }
        //Tomar foto si la creacion del archivo no es nulo
        photoFile?.also {
          val photoURI: Uri = FileProvider.getUriForFile(
            this,
            "com.google.firebase.codelab.mlkit.automl.fileprovider",
            it
          )
          takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
          startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        }
      }
    }
  }

  companion object {
    private const val TAG = "StillImageActivity"
    private const val REQUEST_IMAGE_CAPTURE = 1
    private const val REQUEST_PHOTO_LIBRARY = 2

  }
}
