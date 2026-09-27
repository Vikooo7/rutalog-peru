package com.example.rutalogcliente

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rutalogcliente.data.local.AppDatabase
import com.example.rutalogcliente.ui.navigation.AppNavigation
import com.example.rutalogcliente.ui.theme.RutaLogTheme
import com.example.rutalogcliente.viewmodel.AuthViewModel
import com.example.rutalogcliente.viewmodel.EnvioViewModel

/** RutaLog Perú · App Cliente. Crea la base de datos Room e inicializa los ViewModel. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La barra superior siempre es azul oscuro: íconos del sistema en blanco.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

        val db = AppDatabase.getDB(this)

        setContent {
            RutaLogTheme {
                val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory(db.usuarioDao()))
                val envioViewModel: EnvioViewModel = viewModel(factory = EnvioViewModel.factory(db.envioDao()))
                AppNavigation(authViewModel = authViewModel, envioViewModel = envioViewModel)
            }
        }
    }
}
