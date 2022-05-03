package com.avion.easypermissions

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity

class EasyPermissionManager {
    companion object {
        private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
        private lateinit var operationCall: () -> Unit

        /**
         * In Activity Create Property val as val permissionLauncher =EasyPermissionManager.requestPermissionsAndExecute(this)
         *
         * In Fragment call EasyPermissionManager.requestPermissionsAndExecute(this) in onAttach() method
         *
         * @param activity reference to current activity
         * @param functionCall function that need to be executed after successful permissions
         * @see "github.com/vickyd178/",
         * @author Vicky Dhope
         */
        fun createAndGetPermissionLauncher(
            activity: FragmentActivity,
        ): ActivityResultLauncher<Array<String>>? {
            permissionLauncher =
                activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                    val granted = permissions.entries.all {
                        it.value == true
                    }
                    if (granted) {
                        operationCall.invoke()
                    } else {
                        Toast.makeText(
                            activity,
                            "Permissions Denied! Allow them from System Settings to access more features of application.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            return permissionLauncher
        }

        @Throws(Exception::class)
        fun requestPermissions(
            context: Context,
            permissions: Array<String>,
            functionCall: () -> Unit
        ) {
            context.let {
                if (hasPermissions(context, permissions)) {
                    functionCall.invoke()
                } else {
                    permissionLauncher?.let {
                        requestPermissionsAndExecute(functionCall, permissions)
                    } ?: throw PermissionException(
                        message = "\nNullPointerException: OOPS!, ActivityResultLauncher is not initialized yet,\nmake sure you are defining it outside of onCreate in activity,\nif you are using in fragment then make sure you have initialized in onAttach()," +
                                "\n*******************************************************************************************" +
                                "\nIn Activity: \n\n" +
                                "class MainActivity : AppCompatActivity() {\n" +
                                "\n" +
                                "    val permissionLauncher = EasyPermissionManager.createAndGetPermissionLauncher(this)\n" +
                                "    " +
                                "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
                                "        super.onCreate(savedInstanceState)" +
                                "    }" +
                                "}\n" +
                                "\n********************************************************************************************" +
                                "\nIn Fragment: \n\n" +
                                "\n" +
                                "    override fun onAttach(context: Context) {\n" +
                                "        super.onAttach(context)\n" +
                                "        EasyPermissionManager.createAndGetPermissionLauncher(requireActivity())\n" +
                                "    }",
                        exception = NullPointerException()
                    )
                }
            }
        }

        /*
        * check if your app has permissions permitted previously
        * */
        private fun hasPermissions(
            context: Context,
            permissions: Array<String>
        ): Boolean =
            permissions.all {
                ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }


        /*
        * this function will request permissions and execute given function call after request granted successfully*/
        private fun requestPermissionsAndExecute(
            functionCall: () -> Unit,
            permissions: Array<String>
        ) {

            permissionLauncher?.let {
                operationCall = functionCall
                it.launch(permissions)

            }

        }
    }

}