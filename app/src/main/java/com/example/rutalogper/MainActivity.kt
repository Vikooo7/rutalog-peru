@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.rutalogper

/* ================================================================
   RutaLog Perú — Logística y transporte de carga (alcance nacional)
   Roles: cliente remitente · operador logístico / administrador

   AVANCE ~50 % (entrega para revisión)
   HECHO (datos simulados en memoria, MVVM en un solo archivo):
     RF01 Splash + Login            RF06 Número de guía simulado
     RF02 Acceso cliente/operador   RF07 Búsqueda por número de guía
     RF03 50 envíos de ejemplo      RF08 Línea de tiempo del envío
     RF04 Filtros estado / zona /   RF09 Operador: estado y transportista
          región (las 25 del Perú)
     RF05 Registro de nuevo envío   RF10 Rutas activas y hora de llegada
   PENDIENTE (siguiente entrega):
     - Persistencia (Room / DataStore) y conexión a backend real
     - Mapa de rutas en tiempo real y notificaciones push
     - Perfil, recuperación de contraseña y reportes del operador
     - Pruebas unitarias y de interfaz

   Cuentas demo:  cliente@rutalog.pe / 1234   ·   operador@rutalog.pe / 1234
   ================================================================ */

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La barra superior siempre es azul oscuro: íconos del sistema en blanco.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))

        setContent {
            RutaLogTheme {
                RutaLogApp()
            }
        }
    }
}

/* ============================================================
   1. MODELOS (model)
   Envio, Guia, Ruta, Transportista, EventoSeguimiento, EstadoEnvio
   ============================================================ */

enum class AppScreen(val titulo: String) {
    SPLASH(""),
    LOGIN(""),
    CLIENTE_INICIO("Inicio"),
    NUEVO_ENVIO("Registrar envío"),
    BUSCAR_GUIA("Buscar guía"),
    SEGUIMIENTO("Seguimiento"),
    ENVIO_LIST("Envíos"),
    OPERADOR_DASHBOARD("Panel de operaciones"),
    ENVIO_DETALLE("Detalle del envío"),
    RUTAS_ACTIVAS("Rutas activas")
}

enum class Rol(val etiqueta: String, val descripcion: String) {
    CLIENTE("Cliente", "Registra y rastrea tus envíos"),
    OPERADOR("Operador logístico", "Gestiona envíos y rutas")
}

/** Macrozonas para agrupar las 25 regiones en los filtros. */
enum class Zona(val etiqueta: String) {
    LIMA_CALLAO("Lima y Callao"),
    NORTE("Norte"),
    CENTRO("Centro"),
    SUR("Sur"),
    ORIENTE("Oriente")
}

/** Las 25 regiones del Perú (24 departamentos + Callao) con su ciudad principal de destino. */
enum class Region(val etiqueta: String, val ciudad: String, val zona: Zona) {
    LIMA("Lima", "Lima", Zona.LIMA_CALLAO),
    CALLAO("Callao", "Callao", Zona.LIMA_CALLAO),
    TUMBES("Tumbes", "Tumbes", Zona.NORTE),
    PIURA("Piura", "Piura", Zona.NORTE),
    LAMBAYEQUE("Lambayeque", "Chiclayo", Zona.NORTE),
    LA_LIBERTAD("La Libertad", "Trujillo", Zona.NORTE),
    CAJAMARCA("Cajamarca", "Cajamarca", Zona.NORTE),
    ANCASH("Áncash", "Huaraz", Zona.NORTE),
    HUANUCO("Huánuco", "Huánuco", Zona.CENTRO),
    PASCO("Pasco", "Cerro de Pasco", Zona.CENTRO),
    JUNIN("Junín", "Huancayo", Zona.CENTRO),
    HUANCAVELICA("Huancavelica", "Huancavelica", Zona.CENTRO),
    AYACUCHO("Ayacucho", "Ayacucho", Zona.CENTRO),
    ICA("Ica", "Ica", Zona.SUR),
    APURIMAC("Apurímac", "Abancay", Zona.SUR),
    CUSCO("Cusco", "Cusco", Zona.SUR),
    AREQUIPA("Arequipa", "Arequipa", Zona.SUR),
    PUNO("Puno", "Puno", Zona.SUR),
    MOQUEGUA("Moquegua", "Moquegua", Zona.SUR),
    TACNA("Tacna", "Tacna", Zona.SUR),
    AMAZONAS("Amazonas", "Chachapoyas", Zona.ORIENTE),
    SAN_MARTIN("San Martín", "Tarapoto", Zona.ORIENTE),
    LORETO("Loreto", "Iquitos", Zona.ORIENTE),
    UCAYALI("Ucayali", "Pucallpa", Zona.ORIENTE),
    MADRE_DE_DIOS("Madre de Dios", "Puerto Maldonado", Zona.ORIENTE);

    /** Texto para selectores: "Chiclayo (Lambayeque)". */
    val nombreCompleto: String
        get() = if (ciudad == etiqueta) ciudad else "$ciudad ($etiqueta)"

    companion object {
        fun deCiudad(ciudad: String): Region = entries.firstOrNull { it.ciudad == ciudad } ?: LIMA
    }
}

enum class EstadoEnvio(val etiqueta: String, val paso: Int, val color: Color) {
    PENDIENTE_RECOJO("Pendiente de recojo", 0, Color(0xFF8D6E63)),
    RECOGIDO("Recogido", 1, Color(0xFF1E88E5)),
    EN_TRANSITO("En tránsito", 2, Color(0xFFEF6C00)),
    EN_REPARTO("En reparto", 3, Color(0xFF8E24AA)),
    ENTREGADO("Entregado", 4, Color(0xFF2E7D32));

    val icono: ImageVector
        get() = when (this) {
            PENDIENTE_RECOJO -> Icons.Default.Schedule
            RECOGIDO -> Icons.Default.Inventory
            EN_TRANSITO -> Icons.Default.LocalShipping
            EN_REPARTO -> Icons.Default.LocationOn
            ENTREGADO -> Icons.Default.CheckCircle
        }

    val enCurso: Boolean
        get() = this == RECOGIDO || this == EN_TRANSITO || this == EN_REPARTO
}

data class Guia(
    val numero: String,
    val fechaEmision: LocalDateTime
)

data class Transportista(
    val id: String,
    val nombre: String,
    val empresa: String,
    val placa: String,
    val vehiculo: String,
    val telefono: String
)

data class Ruta(
    val id: String,
    val codigo: String,
    val origen: String,
    val destino: String,
    val distanciaKm: Int,
    val horasViaje: Int,
    val transportistaId: String,
    val modalidad: String = "Terrestre"
) {
    /** Región de la ciudad de destino de la ruta. */
    val region: Region
        get() = Region.deCiudad(destino)
}

data class EventoSeguimiento(
    val estado: EstadoEnvio,
    val fechaHora: LocalDateTime,
    val ubicacion: String,
    val descripcion: String
)

data class Envio(
    val id: Int,
    val guia: Guia,
    val remitente: String,
    val destinatario: String,
    val telefonoDestinatario: String,
    val origen: String,
    val destino: String,
    val rutaId: String,
    val pesoKg: Double,
    val descripcion: String,
    val servicio: String,
    val estado: EstadoEnvio,
    val transportistaId: String?,
    val eventos: List<EventoSeguimiento>,
    val eta: LocalDateTime
) {
    /** Región del destino: la que se usa en los filtros (RF04). */
    val region: Region
        get() = Region.deCiudad(destino)
}

/* ============================================================
   2. REPOSITORIOS (repository) — datos simulados en memoria
   Luego se reemplazan por Room / API sin tocar las pantallas.
   ============================================================ */

const val CLIENTE_DEMO = "Distribuidora Andina SAC"
const val SERVICIO_ESTANDAR = "Estándar"
const val SERVICIO_EXPRESS = "Express"

object RutaRepository {

    val transportistas = listOf(
        Transportista("T01", "Carlos Quispe Mamani", "Transportes Qhapaq Ñan", "AQP-482", "Camión 15 t", "984 112 305"),
        Transportista("T02", "Rosa Huamán Torres", "RutaLog Flota Propia", "BFK-209", "Furgón 4 t", "956 330 871"),
        Transportista("T03", "Jorge Castillo Díaz", "Carga Norte EIRL", "T2P-771", "Tráiler 30 t", "972 845 110"),
        Transportista("T04", "Wilber Condori Apaza", "Transportes Inka Cargo", "X3C-560", "Camión 10 t", "951 207 664"),
        Transportista("T05", "Milagros Rojas Pérez", "RutaLog Flota Propia", "F7R-318", "Camión 8 t", "988 674 023"),
        Transportista("T06", "Luis Sánchez Vera", "Expreso Mochica SAC", "M1L-905", "Camión 12 t", "967 519 448"),
        Transportista("T07", "Edwin Tapullima Sangama", "Transportes Selva Verde", "U4S-218", "Camión 10 t", "942 306 517"),
        Transportista("T08", "Yeni Mamani Choque", "Altiplano Cargo EIRL", "Z9P-640", "Camión 12 t", "951 884 230"),
        Transportista("T09", "Óscar Villanueva Ríos", "Carga Centro Andino", "W2H-377", "Camión 8 t", "964 120 958"),
        Transportista("T10", "Karina Flores Ramos", "RutaLog Flota Propia", "BRL-512", "Furgón 3 t", "993 457 106")
    )

    /** Cobertura nacional: Lima (hub) conecta con las 25 regiones del Perú. */
    val rutas = listOf(
        // Lima y Callao
        Ruta("R01", "LIM-URB", "Lima", "Lima", 45, 3, "T10"),
        Ruta("R02", "LIM-CAL", "Lima", "Callao", 20, 2, "T10"),
        // Norte
        Ruta("R03", "LIM-TUM", "Lima", "Tumbes", 1270, 20, "T03"),
        Ruta("R04", "LIM-PIU", "Lima", "Piura", 980, 15, "T03"),
        Ruta("R05", "LIM-CIX", "Lima", "Chiclayo", 770, 12, "T06"),
        Ruta("R06", "LIM-TRU", "Lima", "Trujillo", 560, 9, "T02"),
        Ruta("R07", "LIM-CAJ", "Lima", "Cajamarca", 860, 14, "T06"),
        Ruta("R08", "LIM-HRZ", "Lima", "Huaraz", 405, 8, "T02"),
        // Centro
        Ruta("R09", "LIM-HCO", "Lima", "Huánuco", 410, 8, "T09"),
        Ruta("R10", "LIM-PAS", "Lima", "Cerro de Pasco", 300, 7, "T09"),
        Ruta("R11", "LIM-HYO", "Lima", "Huancayo", 300, 7, "T05"),
        Ruta("R12", "LIM-HVC", "Lima", "Huancavelica", 450, 10, "T05"),
        Ruta("R13", "LIM-AYA", "Lima", "Ayacucho", 560, 9, "T09"),
        // Sur
        Ruta("R14", "LIM-ICA", "Lima", "Ica", 305, 4, "T04"),
        Ruta("R15", "LIM-ABA", "Lima", "Abancay", 900, 16, "T04"),
        Ruta("R16", "LIM-CUS", "Lima", "Cusco", 1100, 22, "T04"),
        Ruta("R17", "LIM-AQP", "Lima", "Arequipa", 1010, 16, "T01"),
        Ruta("R18", "LIM-PUN", "Lima", "Puno", 1300, 20, "T08"),
        Ruta("R19", "LIM-MOQ", "Lima", "Moquegua", 1140, 17, "T01"),
        Ruta("R20", "LIM-TAC", "Lima", "Tacna", 1290, 19, "T08"),
        // Oriente
        Ruta("R21", "LIM-CHA", "Lima", "Chachapoyas", 1200, 22, "T07"),
        Ruta("R22", "LIM-TPP", "Lima", "Tarapoto", 1390, 24, "T07"),
        Ruta("R23", "LIM-IQT", "Lima", "Iquitos", 1800, 96, "T07", "Terrestre + fluvial (vía Pucallpa)"),
        Ruta("R24", "LIM-PCL", "Lima", "Pucallpa", 780, 18, "T05"),
        Ruta("R25", "LIM-PEM", "Lima", "Puerto Maldonado", 1630, 28, "T08"),
        // Interregionales
        Ruta("R26", "AQP-PUN", "Arequipa", "Puno", 290, 6, "T01"),
        Ruta("R27", "CUS-PEM", "Cusco", "Puerto Maldonado", 480, 10, "T08")
    )

    /** Ciudad principal de cada una de las 25 regiones. */
    val ciudades: List<String> = Region.entries.map { it.ciudad }

    fun ruta(id: String): Ruta = rutas.first { it.id == id }

    fun transportista(id: String?): Transportista? =
        transportistas.firstOrNull { it.id == id }

    fun buscarRuta(origen: String, destino: String): Ruta =
        rutas.firstOrNull { it.origen == origen && it.destino == destino }
            ?: rutas.firstOrNull { it.origen == destino && it.destino == origen }
            ?: rutas.firstOrNull { it.destino == destino }
            ?: rutas.first()
}

object SeguimientoRepository {

    private var correlativo = 100230

    /** RF06: número de guía simulado con dígito verificador. Ej.: RLP-26-100231-7 */
    fun generarNumeroGuia(fecha: LocalDateTime = LocalDateTime.now()): Guia {
        correlativo += 1
        val base = correlativo.toString()
        val verificador = base.sumOf { it.digitToInt() } % 10
        return Guia("RLP-${fecha.year % 100}-$base-$verificador", fecha)
    }

    fun normalizar(texto: String): String =
        texto.uppercase().filter { it.isLetterOrDigit() }

    /** Horas desde la emisión de la guía hasta que ocurre cada estado. */
    fun horasHasta(estado: EstadoEnvio, ruta: Ruta): Long = when (estado) {
        EstadoEnvio.PENDIENTE_RECOJO -> 0L
        EstadoEnvio.RECOGIDO -> 2L
        EstadoEnvio.EN_TRANSITO -> 4L
        EstadoEnvio.EN_REPARTO -> 4L + ruta.horasViaje
        EstadoEnvio.ENTREGADO -> 7L + ruta.horasViaje
    }

    /** Usa el origen y destino del envío: puede pasar por el hub Lima con una ruta troncal. */
    fun evento(
        estado: EstadoEnvio,
        origen: String,
        destino: String,
        ruta: Ruta,
        fecha: LocalDateTime
    ): EventoSeguimiento {
        val tramo = if (ruta.origen == origen) "Ruta ${ruta.codigo}" else "Ruta ${ruta.codigo} (vía hub ${ruta.origen})"
        val (ubicacion, descripcion) = when (estado) {
            EstadoEnvio.PENDIENTE_RECOJO ->
                "Agencia $origen" to "Guía emitida, pendiente de recojo"
            EstadoEnvio.RECOGIDO ->
                "Almacén $origen" to "Carga recogida y verificada"
            EstadoEnvio.EN_TRANSITO -> tramo to if (ruta.modalidad == "Terrestre") {
                "Unidad en camino a $destino"
            } else {
                "En tránsito ${ruta.modalidad.lowercase()} hacia $destino"
            }
            EstadoEnvio.EN_REPARTO ->
                "Hub $destino" to "Salió a reparto con el destinatario"
            EstadoEnvio.ENTREGADO ->
                destino to "Entregado y firmado por el destinatario"
        }
        return EventoSeguimiento(estado, fecha, ubicacion, descripcion)
    }

    fun construirEventos(
        origen: String,
        destino: String,
        ruta: Ruta,
        estado: EstadoEnvio,
        inicio: LocalDateTime
    ): List<EventoSeguimiento> =
        EstadoEnvio.entries
            .filter { it.paso <= estado.paso }
            .map { evento(it, origen, destino, ruta, inicio.plusHours(horasHasta(it, ruta))) }

    fun calcularEta(ruta: Ruta, inicio: LocalDateTime, servicio: String): LocalDateTime =
        inicio.plusHours(ruta.horasViaje + if (servicio == SERVICIO_EXPRESS) 4L else 8L)
}

object EnvioRepository {

    private val _envios = MutableStateFlow(generarSemilla())
    val envios: StateFlow<List<Envio>> = _envios.asStateFlow()

    fun buscarPorGuia(numero: String, lista: List<Envio> = _envios.value): Envio? {
        val buscado = SeguimientoRepository.normalizar(numero)
        if (buscado.isEmpty()) return null
        return lista.firstOrNull { SeguimientoRepository.normalizar(it.guia.numero) == buscado }
    }

    fun porId(id: Int?, lista: List<Envio> = _envios.value): Envio? =
        lista.firstOrNull { it.id == id }

    fun siguienteId(): Int = (_envios.value.maxOfOrNull { it.id } ?: 0) + 1

    fun registrar(envio: Envio) {
        _envios.update { listOf(envio) + it }
    }

    /** RF09: cambia estado y transportista, y ajusta la línea de tiempo. */
    fun actualizarEstado(id: Int, nuevoEstado: EstadoEnvio, transportistaId: String?) {
        val ahora = LocalDateTime.now()
        _envios.update { lista ->
            lista.map { envio ->
                if (envio.id != id) return@map envio
                val ruta = RutaRepository.ruta(envio.rutaId)
                val eventos = when {
                    nuevoEstado.paso > envio.estado.paso -> envio.eventos + EstadoEnvio.entries
                        .filter { it.paso > envio.estado.paso && it.paso <= nuevoEstado.paso }
                        .map { SeguimientoRepository.evento(it, envio.origen, envio.destino, ruta, ahora) }
                    nuevoEstado.paso < envio.estado.paso ->
                        envio.eventos.filter { it.estado.paso <= nuevoEstado.paso }
                    else -> envio.eventos
                }
                envio.copy(
                    estado = nuevoEstado,
                    transportistaId = transportistaId,
                    eventos = eventos,
                    eta = if (nuevoEstado == EstadoEnvio.ENTREGADO) ahora else envio.eta
                )
            }
        }
    }

    /** RF03: 50 envíos de ejemplo repartidos en las 27 rutas (42 del cliente demo). */
    private fun generarSemilla(): List<Envio> {
        val rnd = Random(2026)
        val otrosRemitentes = listOf(
            "Textiles Gamarra EIRL", "AgroExport Norte SAC",
            "Ferretería El Constructor", "Farmacias Salud Perú"
        )
        val destinatarios = listOf(
            "María Fernández", "José Huamán", "Lucía Paredes", "Pedro Castillo R.",
            "Rosa Chávez", "Miguel Torres", "Ana Lucía Rojas", "Jorge Salazar",
            "Carmen Vilca", "Luis Ccori", "Patricia Núñez", "Raúl Mendoza"
        )
        val contenidos = listOf(
            "Cajas de mercadería", "Rollos de tela", "Paletas de palta",
            "Herramientas y repuestos", "Medicamentos (cadena seca)", "Útiles escolares",
            "Electrodomésticos", "Documentos contables", "Calzado por mayor"
        )
        val estados = EstadoEnvio.entries
        val ahora = LocalDateTime.now()

        return (1..50).map { i ->
            // 7 y 27 son coprimos: los primeros 27 envíos pasan por las 27 rutas.
            val ruta = RutaRepository.rutas[(i * 7) % RutaRepository.rutas.size]
            val estado = estados[(i * 3) % estados.size]
            val margenMin = if (estado == EstadoEnvio.ENTREGADO) {
                rnd.nextLong(60, 60L * 48)
            } else {
                rnd.nextLong(20, 170)
            }
            val inicio = ahora
                .minusHours(SeguimientoRepository.horasHasta(estado, ruta))
                .minusMinutes(margenMin)
            val servicio = if (i % 4 == 0) SERVICIO_EXPRESS else SERVICIO_ESTANDAR
            val etaCalculada = SeguimientoRepository.calcularEta(ruta, inicio, servicio)
            val eta = when {
                estado == EstadoEnvio.ENTREGADO ->
                    inicio.plusHours(SeguimientoRepository.horasHasta(estado, ruta))
                etaCalculada.isBefore(ahora) -> ahora.plusMinutes(rnd.nextLong(30, 150))
                else -> etaCalculada
            }

            Envio(
                id = i,
                guia = SeguimientoRepository.generarNumeroGuia(inicio),
                remitente = if (i % 6 == 0) otrosRemitentes[(i / 6) % otrosRemitentes.size] else CLIENTE_DEMO,
                destinatario = destinatarios[(i * 7) % destinatarios.size],
                telefonoDestinatario = "9" + (10_000_000 + rnd.nextInt(89_999_999)),
                origen = ruta.origen,
                destino = ruta.destino,
                rutaId = ruta.id,
                pesoKg = rnd.nextInt(5, 18_000) / 10.0,
                descripcion = contenidos[(i * 2) % contenidos.size],
                servicio = servicio,
                estado = estado,
                transportistaId = if (estado == EstadoEnvio.PENDIENTE_RECOJO) null else ruta.transportistaId,
                eventos = SeguimientoRepository.construirEventos(ruta.origen, ruta.destino, ruta, estado, inicio),
                eta = eta
            )
        }.sortedByDescending { it.guia.fechaEmision }
    }
}

/* ============================================================
   3. VIEWMODELS (viewmodel)
   AuthViewModel, EnvioViewModel, SeguimientoViewModel,
   OperacionLogisticaViewModel, RutaViewModel
   ============================================================ */

data class AuthUiState(
    val cargando: Boolean = false,
    val error: String? = null,
    val intentosFallidos: Int = 0,
    val rol: Rol? = null,
    val nombre: String = "",
    val correo: String = ""
)

class AuthViewModel : ViewModel() {

    private val _estado = MutableStateFlow(AuthUiState())
    val estado: StateFlow<AuthUiState> = _estado.asStateFlow()

    fun login(correo: String, clave: String, rol: Rol) {
        val correoLimpio = correo.trim()
        val error = when {
            !correoLimpio.contains("@") || !correoLimpio.contains(".") -> "Ingresa un correo válido."
            clave.length < 4 -> "La contraseña debe tener al menos 4 caracteres."
            else -> null
        }
        if (error != null) {
            _estado.update { it.copy(error = error, intentosFallidos = it.intentosFallidos + 1) }
            return
        }
        viewModelScope.launch {
            _estado.update { it.copy(cargando = true, error = null) }
            delay(1200) // Simula la verificación con el servidor.
            _estado.value = AuthUiState(
                rol = rol,
                nombre = if (rol == Rol.CLIENTE) CLIENTE_DEMO else "Central de Operaciones Lima",
                correo = correoLimpio
            )
        }
    }

    fun limpiarError() {
        _estado.update { it.copy(error = null) }
    }

    fun cerrarSesion() {
        _estado.value = AuthUiState()
    }
}

data class NuevoEnvioForm(
    val remitente: String = CLIENTE_DEMO,
    val destinatario: String = "",
    val telefono: String = "",
    val origen: String = "Lima",
    val destino: String = "Arequipa",
    val peso: String = "",
    val descripcion: String = "",
    val servicio: String = SERVICIO_ESTANDAR
)

data class FiltrosEnvio(
    val estado: EstadoEnvio? = null,
    val zona: Zona? = null,
    val region: Region? = null,
    val texto: String = ""
) {
    val activos: Boolean
        get() = estado != null || zona != null || region != null || texto.isNotEmpty()
}

class EnvioViewModel : ViewModel() {

    private val _filtros = MutableStateFlow(FiltrosEnvio())
    val filtros: StateFlow<FiltrosEnvio> = _filtros.asStateFlow()

    private val remitenteFlow = MutableStateFlow<String?>(null)

    /** RF03 + RF04: lista filtrada por estado, macrozona, región y texto. */
    val enviosFiltrados: StateFlow<List<Envio>> = combine(
        EnvioRepository.envios, _filtros, remitenteFlow
    ) { lista, filtros, remitente ->
        filtrarEnvios(lista, filtros, remitente)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        filtrarEnvios(EnvioRepository.envios.value, FiltrosEnvio(), null)
    )

    val misEnvios: StateFlow<List<Envio>> = combine(
        EnvioRepository.envios, remitenteFlow
    ) { lista, remitente ->
        if (remitente == null) lista else lista.filter { it.remitente == remitente }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EnvioRepository.envios.value)

    /** El cliente solo ve sus envíos; el operador ve todos (null). */
    fun establecerAlcance(remitente: String?) {
        remitenteFlow.value = remitente
        limpiarFiltros()
    }

    fun filtrarPorEstado(estado: EstadoEnvio?) {
        _filtros.update { it.copy(estado = estado) }
    }

    /** Al cambiar de macrozona se descarta una región que no pertenezca a ella. */
    fun filtrarPorZona(zona: Zona?) {
        _filtros.update { actual ->
            actual.copy(
                zona = zona,
                region = actual.region?.takeIf { zona == null || it.zona == zona }
            )
        }
    }

    fun filtrarPorRegion(region: Region?) {
        _filtros.update { it.copy(region = region, zona = region?.zona ?: it.zona) }
    }

    fun buscarTexto(texto: String) {
        _filtros.update { it.copy(texto = texto) }
    }

    fun limpiarFiltros() {
        _filtros.value = FiltrosEnvio()
    }

    fun validar(form: NuevoEnvioForm): String? {
        val peso = form.peso.toDoubleOrNull()
        return when {
            form.remitente.isBlank() -> "Ingresa el nombre del remitente."
            form.destinatario.trim().length < 3 -> "Ingresa el nombre completo del destinatario."
            form.telefono.length != 9 || !form.telefono.startsWith("9") ->
                "El teléfono debe tener 9 dígitos y empezar con 9."
            form.origen == form.destino && form.origen != "Lima" ->
                "El origen y el destino deben ser distintos."
            peso == null || peso <= 0.0 || peso > 30_000.0 ->
                "Ingresa un peso válido entre 0.1 y 30 000 kg."
            form.descripcion.isBlank() -> "Describe brevemente el contenido."
            else -> null
        }
    }

    /** RF05 + RF06: registra el envío y genera su número de guía. */
    fun registrar(form: NuevoEnvioForm): Envio {
        val ruta = RutaRepository.buscarRuta(form.origen, form.destino)
        val ahora = LocalDateTime.now()
        val envio = Envio(
            id = EnvioRepository.siguienteId(),
            guia = SeguimientoRepository.generarNumeroGuia(ahora),
            remitente = form.remitente.trim(),
            destinatario = form.destinatario.trim(),
            telefonoDestinatario = form.telefono,
            origen = form.origen,
            destino = form.destino,
            rutaId = ruta.id,
            pesoKg = form.peso.toDoubleOrNull() ?: 0.0,
            descripcion = form.descripcion.trim(),
            servicio = form.servicio,
            estado = EstadoEnvio.PENDIENTE_RECOJO,
            transportistaId = null,
            eventos = SeguimientoRepository.construirEventos(
                form.origen, form.destino, ruta, EstadoEnvio.PENDIENTE_RECOJO, ahora
            ),
            eta = SeguimientoRepository.calcularEta(ruta, ahora, form.servicio)
        )
        EnvioRepository.registrar(envio)
        return envio
    }
}

private fun filtrarEnvios(
    lista: List<Envio>,
    filtros: FiltrosEnvio,
    remitente: String?
): List<Envio> {
    val buscado = filtros.texto.trim()
    val guiaBuscada = SeguimientoRepository.normalizar(buscado)
    return lista.filter { envio ->
        (remitente == null || envio.remitente == remitente) &&
            (filtros.estado == null || envio.estado == filtros.estado) &&
            (filtros.zona == null || envio.region.zona == filtros.zona) &&
            (filtros.region == null || envio.region == filtros.region) &&
            (buscado.isEmpty() ||
                SeguimientoRepository.normalizar(envio.guia.numero).contains(guiaBuscada) ||
                envio.destinatario.contains(buscado, ignoreCase = true) ||
                envio.destino.contains(buscado, ignoreCase = true) ||
                envio.origen.contains(buscado, ignoreCase = true) ||
                envio.region.etiqueta.contains(buscado, ignoreCase = true))
    }
}

class SeguimientoViewModel : ViewModel() {

    private val guiaSeguida = MutableStateFlow<String?>(null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _recientes = MutableStateFlow<List<String>>(emptyList())
    val recientes: StateFlow<List<String>> = _recientes.asStateFlow()

    /** Se recalcula si el operador cambia el estado mientras el cliente mira. */
    val envio: StateFlow<Envio?> = combine(EnvioRepository.envios, guiaSeguida) { lista, guia ->
        guia?.let { EnvioRepository.buscarPorGuia(it, lista) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** RF07: búsqueda por número de guía (acepta con o sin guiones). */
    fun buscar(numero: String): Boolean {
        val encontrado = EnvioRepository.buscarPorGuia(numero)
        if (encontrado == null) {
            _error.value = if (numero.isBlank()) {
                "Ingresa un número de guía."
            } else {
                "No encontramos la guía ${numero.trim().uppercase()}. Verifica el número."
            }
            return false
        }
        seguir(encontrado.guia.numero)
        return true
    }

    fun seguir(numeroGuia: String) {
        _error.value = null
        guiaSeguida.value = numeroGuia
        _recientes.update { (listOf(numeroGuia) + it).distinct().take(5) }
    }

    fun limpiarError() {
        _error.value = null
    }
}

data class ResumenOperacion(
    val total: Int = 0,
    val porEstado: Map<EstadoEnvio, Int> = emptyMap(),
    val sinTransportista: Int = 0,
    val entregadosHoy: Int = 0,
    /** Envíos no entregados por macrozona. */
    val activosPorZona: Map<Zona, Int> = emptyMap(),
    /** Regiones (de las 25) que tienen al menos un envío registrado. */
    val regionesAtendidas: Set<Region> = emptySet()
)

class OperacionLogisticaViewModel : ViewModel() {

    private val seleccionadoId = MutableStateFlow<Int?>(null)

    val transportistas: List<Transportista> = RutaRepository.transportistas

    val envioSeleccionado: StateFlow<Envio?> = combine(EnvioRepository.envios, seleccionadoId) { lista, id ->
        EnvioRepository.porId(id, lista)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val resumen: StateFlow<ResumenOperacion> = EnvioRepository.envios
        .map { calcularResumen(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, calcularResumen(EnvioRepository.envios.value))

    val pendientesAsignacion: StateFlow<List<Envio>> = EnvioRepository.envios
        .map { lista -> lista.filter { it.transportistaId == null && it.estado != EstadoEnvio.ENTREGADO } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun seleccionar(id: Int) {
        seleccionadoId.value = id
    }

    /** RF09: devuelve un mensaje de error o null si se guardó. */
    fun actualizar(id: Int, estado: EstadoEnvio, transportistaId: String?): String? {
        if (estado.paso >= EstadoEnvio.RECOGIDO.paso && transportistaId == null) {
            return "Asigna un transportista antes de pasar a \"${estado.etiqueta}\"."
        }
        EnvioRepository.actualizarEstado(id, estado, transportistaId)
        return null
    }
}

private fun calcularResumen(lista: List<Envio>): ResumenOperacion {
    val hoy = LocalDate.now()
    return ResumenOperacion(
        total = lista.size,
        porEstado = EstadoEnvio.entries.associateWith { estado -> lista.count { it.estado == estado } },
        sinTransportista = lista.count { it.transportistaId == null && it.estado != EstadoEnvio.ENTREGADO },
        entregadosHoy = lista.count { envio ->
            envio.estado == EstadoEnvio.ENTREGADO &&
                envio.eventos.last().fechaHora.toLocalDate() == hoy
        },
        activosPorZona = Zona.entries.associateWith { zona ->
            lista.count { it.region.zona == zona && it.estado != EstadoEnvio.ENTREGADO }
        },
        regionesAtendidas = lista.map { it.region }.toSet()
    )
}

data class RutaActiva(
    val ruta: Ruta,
    val transportista: Transportista?,
    val envios: List<Envio>,
    val proximaLlegada: LocalDateTime,
    val progreso: Float
)

class RutaViewModel : ViewModel() {

    /** RF10: rutas con carga en curso y su próxima hora de llegada. */
    val rutasActivas: StateFlow<List<RutaActiva>> = EnvioRepository.envios
        .map { calcularRutasActivas(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, calcularRutasActivas(EnvioRepository.envios.value))

    val rutasSinCarga: StateFlow<List<Ruta>> = rutasActivas
        .map { activas -> RutaRepository.rutas.filter { ruta -> activas.none { it.ruta.id == ruta.id } } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

private fun calcularRutasActivas(lista: List<Envio>): List<RutaActiva> =
    RutaRepository.rutas.mapNotNull { ruta ->
        val enCurso = lista.filter { it.rutaId == ruta.id && it.estado.enCurso }
        if (enCurso.isEmpty()) {
            null
        } else {
            RutaActiva(
                ruta = ruta,
                transportista = RutaRepository.transportista(ruta.transportistaId),
                envios = enCurso.sortedBy { it.eta },
                proximaLlegada = enCurso.minOf { it.eta },
                progreso = enCurso.map { progresoViaje(it) }.average().toFloat()
            )
        }
    }.sortedBy { it.proximaLlegada }

fun progresoViaje(envio: Envio): Float {
    if (envio.estado == EstadoEnvio.ENTREGADO) return 1f
    val total = Duration.between(envio.guia.fechaEmision, envio.eta).toMinutes().coerceAtLeast(1)
    val transcurrido = Duration.between(envio.guia.fechaEmision, LocalDateTime.now()).toMinutes()
    return (transcurrido.toFloat() / total).coerceIn(0f, 0.97f)
}

/* ============================================================
   4. COLORES Y TEMA MATERIAL 3
   ============================================================ */

// Paleta tomada del logo: azul marino (texto "RUTALOG") y rojo (caja, flecha y pin).
private val AzulRuta = Color(0xFF0B2F5B)
private val AzulRutaClaro = Color(0xFF1B4F8A)
private val RojoRuta = Color(0xFFE3192A)
private val VerdeEntrega = Color(0xFF2E7D32)

private val LightColors = lightColorScheme(
    primary = AzulRuta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = RojoRuta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDAD6),
    onSecondaryContainer = Color(0xFF410003),
    tertiary = VerdeEntrega,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8F0B9),
    onTertiaryContainer = Color(0xFF002106),
    background = Color(0xFFF4F6F9),
    onBackground = Color(0xFF191C1E),
    surface = Color.White,
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41474D),
    outline = Color(0xFF71787E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF002F64),
    primaryContainer = Color(0xFF15457F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFFFB3AC),
    onSecondary = Color(0xFF68000B),
    secondaryContainer = Color(0xFF930015),
    onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFF9CD49E),
    onTertiary = Color(0xFF00390F),
    tertiaryContainer = Color(0xFF16521F),
    onTertiaryContainer = Color(0xFFB8F0B9),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFE0E3E6),
    surface = Color(0xFF171C20),
    onSurface = Color(0xFFE0E3E6),
    surfaceVariant = Color(0xFF41474D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9198)
)

@Composable
fun RutaLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}

/* ============================================================
   5. APP PRINCIPAL Y NAVEGACIÓN
   Splash → Login
   Cliente  → Inicio → Registrar envío / Buscar guía → Seguimiento
   Operador → Dashboard → Envíos → Detalle → Actualizar → Rutas
   ============================================================ */

@Composable
fun RutaLogApp(
    authVm: AuthViewModel = viewModel(),
    envioVm: EnvioViewModel = viewModel(),
    seguimientoVm: SeguimientoViewModel = viewModel(),
    operacionVm: OperacionLogisticaViewModel = viewModel(),
    rutaVm: RutaViewModel = viewModel()
) {
    val auth by authVm.estado.collectAsState()
    var pila by rememberSaveable { mutableStateOf(listOf(AppScreen.SPLASH)) }
    val actual = pila.last()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val mostrarMensaje: (String) -> Unit = { mensaje ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(mensaje)
        }
    }

    val raiz = if (auth.rol == Rol.OPERADOR) AppScreen.OPERADOR_DASHBOARD else AppScreen.CLIENTE_INICIO

    fun navegar(destino: AppScreen) {
        if (pila.last() != destino) pila = pila + destino
    }

    fun irAPestana(destino: AppScreen) {
        pila = if (destino == raiz) listOf(raiz) else listOf(raiz, destino)
    }

    fun volver() {
        if (pila.size > 1) pila = pila.dropLast(1)
    }

    LaunchedEffect(auth.rol) {
        val rol = auth.rol
        envioVm.establecerAlcance(if (rol == Rol.CLIENTE) auth.nombre else null)
        if (rol != null && pila.last() == AppScreen.LOGIN) pila = listOf(raiz)
    }

    BackHandler(enabled = pila.size > 1) { volver() }

    val zona = when (actual) {
        AppScreen.SPLASH -> 0
        AppScreen.LOGIN -> 1
        else -> 2
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = zona,
            transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(300)) },
            label = "ZonaApp"
        ) { zonaActual ->
            when (zonaActual) {
                0 -> SplashScreen(
                    onFinish = {
                        pila = listOf(if (auth.rol != null) raiz else AppScreen.LOGIN)
                    }
                )

                1 -> LoginScreen(
                    estado = auth,
                    onLogin = authVm::login,
                    onLimpiarError = authVm::limpiarError
                )

                else -> {
                    val rol = auth.rol ?: Rol.CLIENTE
                    RutaLogShell(
                        pantalla = actual,
                        rol = rol,
                        nombreUsuario = auth.nombre,
                        puedeVolver = pila.size > 1,
                        snackbarHostState = snackbarHostState,
                        onVolver = { volver() },
                        onPestana = { irAPestana(it) },
                        onCerrarSesion = {
                            authVm.cerrarSesion()
                            pila = listOf(AppScreen.LOGIN)
                        }
                    ) {
                        AnimatedContent(
                            targetState = actual,
                            transitionSpec = {
                                (fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 10 })
                                    .togetherWith(fadeOut(tween(150)))
                            },
                            label = "NavegacionPantallas"
                        ) { pantalla ->
                            ContenidoPantalla(
                                pantalla = pantalla,
                                rol = rol,
                                nombreUsuario = auth.nombre,
                                envioVm = envioVm,
                                seguimientoVm = seguimientoVm,
                                operacionVm = operacionVm,
                                rutaVm = rutaVm,
                                navegar = { navegar(it) },
                                irAPestana = { irAPestana(it) },
                                volver = { volver() },
                                mostrarMensaje = mostrarMensaje
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContenidoPantalla(
    pantalla: AppScreen,
    rol: Rol,
    nombreUsuario: String,
    envioVm: EnvioViewModel,
    seguimientoVm: SeguimientoViewModel,
    operacionVm: OperacionLogisticaViewModel,
    rutaVm: RutaViewModel,
    navegar: (AppScreen) -> Unit,
    irAPestana: (AppScreen) -> Unit,
    volver: () -> Unit,
    mostrarMensaje: (String) -> Unit
) {
    val esOperador = rol == Rol.OPERADOR

    val abrirEnvio: (Envio) -> Unit = { envio ->
        if (esOperador) {
            operacionVm.seleccionar(envio.id)
            navegar(AppScreen.ENVIO_DETALLE)
        } else {
            seguimientoVm.seguir(envio.guia.numero)
            navegar(AppScreen.SEGUIMIENTO)
        }
    }

    when (pantalla) {
        AppScreen.CLIENTE_INICIO -> {
            val misEnvios by envioVm.misEnvios.collectAsState()
            ClienteInicioScreen(
                nombre = nombreUsuario,
                envios = misEnvios,
                onRegistrar = { irAPestana(AppScreen.NUEVO_ENVIO) },
                onRastrear = { irAPestana(AppScreen.BUSCAR_GUIA) },
                onMisEnvios = { irAPestana(AppScreen.ENVIO_LIST) },
                onRastreoRapido = { texto ->
                    if (seguimientoVm.buscar(texto)) {
                        navegar(AppScreen.SEGUIMIENTO)
                    } else {
                        mostrarMensaje(seguimientoVm.error.value ?: "Guía no encontrada.")
                    }
                },
                onEnvio = abrirEnvio
            )
        }

        AppScreen.NUEVO_ENVIO -> NuevoEnvioScreen(
            remitente = nombreUsuario,
            onValidar = envioVm::validar,
            onRegistrar = envioVm::registrar,
            onVerSeguimiento = { guia ->
                seguimientoVm.seguir(guia)
                navegar(AppScreen.SEGUIMIENTO)
            }
        )

        AppScreen.BUSCAR_GUIA -> {
            val error by seguimientoVm.error.collectAsState()
            val recientes by seguimientoVm.recientes.collectAsState()
            val misEnvios by envioVm.misEnvios.collectAsState()
            BuscarGuiaScreen(
                error = error,
                recientes = recientes,
                sugerencias = misEnvios.filter { it.estado.enCurso }.take(3).map { it.guia.numero },
                onBuscar = { numero ->
                    if (seguimientoVm.buscar(numero)) navegar(AppScreen.SEGUIMIENTO)
                },
                onLimpiarError = seguimientoVm::limpiarError
            )
        }

        AppScreen.SEGUIMIENTO -> {
            val envio by seguimientoVm.envio.collectAsState()
            SeguimientoScreen(
                envio = envio,
                onBuscarOtra = {
                    if (esOperador) volver() else irAPestana(AppScreen.BUSCAR_GUIA)
                }
            )
        }

        AppScreen.ENVIO_LIST -> {
            val envios by envioVm.enviosFiltrados.collectAsState()
            val filtros by envioVm.filtros.collectAsState()
            EnvioListScreen(
                envios = envios,
                filtros = filtros,
                onEstado = envioVm::filtrarPorEstado,
                onZona = envioVm::filtrarPorZona,
                onRegion = envioVm::filtrarPorRegion,
                onTexto = envioVm::buscarTexto,
                onLimpiar = envioVm::limpiarFiltros,
                onEnvio = abrirEnvio
            )
        }

        AppScreen.OPERADOR_DASHBOARD -> {
            val resumen by operacionVm.resumen.collectAsState()
            val rutasActivas by rutaVm.rutasActivas.collectAsState()
            val pendientes by operacionVm.pendientesAsignacion.collectAsState()
            OperadorDashboardScreen(
                resumen = resumen,
                rutasActivas = rutasActivas,
                pendientes = pendientes,
                onVerEstado = { estado ->
                    envioVm.limpiarFiltros()
                    envioVm.filtrarPorEstado(estado)
                    irAPestana(AppScreen.ENVIO_LIST)
                },
                onVerRutas = { irAPestana(AppScreen.RUTAS_ACTIVAS) },
                onEnvio = abrirEnvio
            )
        }

        AppScreen.ENVIO_DETALLE -> {
            val envio by operacionVm.envioSeleccionado.collectAsState()
            EnvioDetalleScreen(
                envio = envio,
                transportistas = operacionVm.transportistas,
                onGuardar = operacionVm::actualizar,
                onMensaje = mostrarMensaje,
                onVerRutas = { navegar(AppScreen.RUTAS_ACTIVAS) }
            )
        }

        AppScreen.RUTAS_ACTIVAS -> {
            val rutasActivas by rutaVm.rutasActivas.collectAsState()
            val rutasSinCarga by rutaVm.rutasSinCarga.collectAsState()
            RutasActivasScreen(
                rutasActivas = rutasActivas,
                rutasSinCarga = rutasSinCarga,
                onEnvio = abrirEnvio
            )
        }

        AppScreen.SPLASH, AppScreen.LOGIN -> Unit
    }
}

/* ============================================================
   6. SPLASH (RF01)
   ============================================================ */

@Composable
fun SplashScreen(onFinish: () -> Unit) {
    val finalizar by rememberUpdatedState(onFinish)
    val escala = remember { Animatable(0.7f) }
    val alfa = remember { Animatable(0f) }

    val transicion = rememberInfiniteTransition(label = "CamionSplash")
    val avance by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AvanceCamion"
    )

    LaunchedEffect(Unit) {
        launch { escala.animateTo(1f, tween(700, easing = EaseOutBack)) }
        alfa.animateTo(1f, tween(700))
        delay(1700)
        finalizar()
    }

    // Fondo blanco igual al del logo: el paso ícono → splash del sistema → este splash se ve continuo.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .scale(escala.value)
                .alpha(alfa.value)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_rutalog),
                contentDescription = "Logo RutaLog Perú",
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(620f / 665f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Tu carga ubicada de costa a selva",
                color = AzulRuta,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .width(220.dp)
                    .height(30.dp)
            ) {
                val ancho = maxWidth
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(AzulRuta.copy(alpha = 0.15f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .width(ancho * avance)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(RojoRuta)
                )
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = RojoRuta,
                    modifier = Modifier
                        .offset(x = (ancho - 24.dp) * avance)
                        .size(24.dp)
                )
            }
        }

        Text(
            text = "Logística y transporte de carga · v0.5",
            color = AzulRuta.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        )
    }
}

/* ============================================================
   7. LOGIN (RF01 + RF02: acceso como cliente u operador)
   ============================================================ */

@Composable
fun LoginScreen(
    estado: AuthUiState,
    onLogin: (String, String, Rol) -> Unit,
    onLimpiarError: () -> Unit
) {
    var rol by rememberSaveable { mutableStateOf(Rol.CLIENTE) }
    var correo by rememberSaveable { mutableStateOf("") }
    var clave by rememberSaveable { mutableStateOf("") }
    var verClave by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val sacudida = remember { Animatable(0f) }
    LaunchedEffect(estado.intentosFallidos) {
        if (estado.intentosFallidos > 0) {
            repeat(3) {
                sacudida.animateTo(12f, tween(50))
                sacudida.animateTo(-12f, tween(50))
            }
            sacudida.animateTo(0f, tween(50))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.verticalGradient(listOf(AzulRuta, AzulRutaClaro)))
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 8.dp
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_rutalog),
                        contentDescription = "Logo RutaLog Perú",
                        modifier = Modifier
                            .padding(14.dp)
                            .width(150.dp)
                            .aspectRatio(620f / 665f)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Registra, rastrea y gestiona tu carga en todo el Perú.",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "¿Cómo deseas ingresar?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Rol.entries.forEach { opcion ->
                    RolCard(
                        rol = opcion,
                        seleccionado = rol == opcion,
                        onClick = {
                            rol = opcion
                            onLimpiarError()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = sacudida.value.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Iniciar sesión",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = correo,
                        onValueChange = {
                            correo = it.trim()
                            onLimpiarError()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Correo electrónico") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        singleLine = true,
                        isError = estado.error != null
                    )

                    OutlinedTextField(
                        value = clave,
                        onValueChange = {
                            clave = it
                            onLimpiarError()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { verClave = !verClave }) {
                                Icon(
                                    imageVector = if (verClave) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (verClave) "Ocultar contraseña" else "Mostrar contraseña"
                                )
                            }
                        },
                        visualTransformation = if (verClave) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { onLogin(correo, clave, rol) }),
                        singleLine = true,
                        isError = estado.error != null
                    )

                    AnimatedVisibility(visible = estado.error != null) {
                        MensajeError(texto = estado.error ?: "")
                    }

                    Button(
                        onClick = { onLogin(correo, clave, rol) },
                        enabled = !estado.cargando,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        AnimatedContent(targetState = estado.cargando, label = "BotonLogin") { cargando ->
                            if (cargando) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Verificando…")
                                }
                            } else {
                                Text(if (rol == Rol.CLIENTE) "Ingresar como cliente" else "Ingresar como operador")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                correo = if (rol == Rol.CLIENTE) "cliente@rutalog.pe" else "operador@rutalog.pe"
                                clave = "1234"
                                onLimpiarError()
                            }
                        ) {
                            Text("Usar cuenta demo")
                        }
                        TextButton(onClick = { proximamente(context, "Recuperar contraseña") }) {
                            Text("¿Olvidaste tu clave?")
                        }
                    }
                }
            }

            Text(
                text = "Demo: cliente@rutalog.pe u operador@rutalog.pe · clave 1234",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
fun RolCard(
    rol: Rol,
    seleccionado: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borde by animateColorAsState(
        targetValue = if (seleccionado) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
        label = "BordeRol"
    )
    val fondo by animateColorAsState(
        targetValue = if (seleccionado) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        label = "FondoRol"
    )

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = fondo),
        border = BorderStroke(2.dp, borde)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = if (rol == Rol.CLIENTE) Icons.Default.Person else Icons.Default.SupportAgent,
                contentDescription = null,
                tint = if (seleccionado) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = rol.etiqueta,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = rol.descripcion,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ============================================================
   8. ESTRUCTURA PRINCIPAL: TOP BAR + BOTTOM BAR POR ROL
   ============================================================ */

data class Pestana(
    val etiqueta: String,
    val icono: ImageVector,
    val destino: AppScreen
)

fun pestanasDe(rol: Rol): List<Pestana> = when (rol) {
    Rol.CLIENTE -> listOf(
        Pestana("Inicio", Icons.Default.Home, AppScreen.CLIENTE_INICIO),
        Pestana("Registrar", Icons.Default.AddBox, AppScreen.NUEVO_ENVIO),
        Pestana("Rastrear", Icons.Default.Search, AppScreen.BUSCAR_GUIA),
        Pestana("Mis envíos", Icons.Default.Inventory, AppScreen.ENVIO_LIST)
    )
    Rol.OPERADOR -> listOf(
        Pestana("Panel", Icons.Default.Dashboard, AppScreen.OPERADOR_DASHBOARD),
        Pestana("Envíos", Icons.Default.Inventory, AppScreen.ENVIO_LIST),
        Pestana("Rutas", Icons.Default.Map, AppScreen.RUTAS_ACTIVAS)
    )
}

fun pestanaDe(pantalla: AppScreen, rol: Rol): AppScreen = when (pantalla) {
    AppScreen.SEGUIMIENTO -> if (rol == Rol.CLIENTE) AppScreen.BUSCAR_GUIA else AppScreen.ENVIO_LIST
    AppScreen.ENVIO_DETALLE -> AppScreen.ENVIO_LIST
    else -> pantalla
}

@Composable
fun RutaLogShell(
    pantalla: AppScreen,
    rol: Rol,
    nombreUsuario: String,
    puedeVolver: Boolean,
    snackbarHostState: SnackbarHostState,
    onVolver: () -> Unit,
    onPestana: (AppScreen) -> Unit,
    onCerrarSesion: () -> Unit,
    content: @Composable () -> Unit
) {
    var confirmarSalida by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val titulo = if (pantalla == AppScreen.ENVIO_LIST && rol == Rol.CLIENTE) "Mis envíos" else pantalla.titulo

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = titulo,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${if (rol == Rol.CLIENTE) "Cliente" else "Operador"} · $nombreUsuario",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    if (puedeVolver) {
                        IconButton(onClick = onVolver) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp, end = 4.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                contentDescription = "RutaLog Perú",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .scale(1.7f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { proximamente(context, "Las notificaciones") }) {
                        BadgedBox(badge = { Badge { Text("2") } }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notificaciones")
                        }
                    }
                    IconButton(onClick = { confirmarSalida = true }) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = "Cerrar sesión")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AzulRuta,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            val seleccionada = pestanaDe(pantalla, rol)
            NavigationBar {
                pestanasDe(rol).forEach { pestana ->
                    NavigationBarItem(
                        selected = seleccionada == pestana.destino,
                        onClick = { onPestana(pestana.destino) },
                        icon = { Icon(pestana.icono, contentDescription = pestana.etiqueta) },
                        label = { Text(pestana.etiqueta) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            content()
        }
    }

    if (confirmarSalida) {
        AlertDialog(
            onDismissRequest = { confirmarSalida = false },
            icon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null) },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Volverás a la pantalla de inicio de sesión.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarSalida = false
                        onCerrarSesion()
                    }
                ) { Text("Cerrar sesión") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarSalida = false }) { Text("Cancelar") }
            }
        )
    }
}

/* ============================================================
   9. CLIENTE: INICIO
   ============================================================ */

@Composable
fun ClienteInicioScreen(
    nombre: String,
    envios: List<Envio>,
    onRegistrar: () -> Unit,
    onRastrear: () -> Unit,
    onMisEnvios: () -> Unit,
    onRastreoRapido: (String) -> Unit,
    onEnvio: (Envio) -> Unit
) {
    var rapido by rememberSaveable { mutableStateOf("") }
    val activos = envios.count { it.estado != EstadoEnvio.ENTREGADO }
    val enCamino = envios.count { it.estado == EstadoEnvio.EN_TRANSITO || it.estado == EstadoEnvio.EN_REPARTO }
    val entregados = envios.count { it.estado == EstadoEnvio.ENTREGADO }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BannerDegradado {
                Text(
                    text = "Hola,",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = nombre,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    EstadisticaBanner(activos, "Activos", Modifier.weight(1f))
                    EstadisticaBanner(enCamino, "En camino", Modifier.weight(1f))
                    EstadisticaBanner(entregados, "Entregados", Modifier.weight(1f))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccionRapida(
                    titulo = "Registrar envío",
                    subtitulo = "Genera tu guía al instante",
                    icono = Icons.Default.AddBox,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onRegistrar,
                    modifier = Modifier.weight(1f)
                )
                AccionRapida(
                    titulo = "Rastrear guía",
                    subtitulo = "Consulta el estado de tu carga",
                    icono = Icons.Default.Search,
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = onRastrear,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Rastreo rápido",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rapido,
                        onValueChange = { rapido = it.uppercase() },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("RLP-26-100231-7") },
                        leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { onRastreoRapido(rapido) }) {
                                Icon(Icons.Default.Search, contentDescription = "Buscar guía")
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(onSearch = { onRastreoRapido(rapido) }),
                        singleLine = true
                    )
                }
            }
        }

        item {
            TituloSeccion(
                titulo = "Mis envíos recientes",
                accion = "Ver todos (${envios.size})",
                onAccion = onMisEnvios
            )
        }

        items(envios.take(5), key = { it.id }) { envio ->
            EnvioCard(envio = envio, onClick = { onEnvio(envio) })
        }
    }
}

@Composable
fun BannerDegradado(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(AzulRuta, AzulRutaClaro)))
    ) {
        Icon(
            imageVector = Icons.Default.LocalShipping,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.08f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(120.dp)
                .offset(x = 20.dp, y = 20.dp)
        )
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun EstadisticaBanner(valor: Int, etiqueta: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        NumeroAnimado(
            valor = valor,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = etiqueta,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun AccionRapida(
    titulo: String,
    subtitulo: String,
    icono: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icono, contentDescription = null, tint = color)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = titulo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitulo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ============================================================
   10. CLIENTE: REGISTRAR ENVÍO (RF05 + RF06)
   ============================================================ */

@Composable
fun NuevoEnvioScreen(
    remitente: String,
    onValidar: (NuevoEnvioForm) -> String?,
    onRegistrar: (NuevoEnvioForm) -> Envio,
    onVerSeguimiento: (String) -> Unit
) {
    var form by remember { mutableStateOf(NuevoEnvioForm(remitente = remitente)) }
    var error by remember { mutableStateOf<String?>(null) }
    var enviando by remember { mutableStateOf(false) }
    var registrado by remember { mutableStateOf<Envio?>(null) }
    val scope = rememberCoroutineScope()
    val ruta = RutaRepository.buscarRuta(form.origen, form.destino)

    fun actualizar(nuevo: NuevoEnvioForm) {
        form = nuevo
        error = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Completa los datos y generaremos tu número de guía al instante.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SeccionFormulario(titulo = "Remitente y destinatario", icono = Icons.Default.Person) {
            OutlinedTextField(
                value = form.remitente,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Remitente") },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                supportingText = { Text("Cuenta con la que iniciaste sesión") },
                singleLine = true
            )
            OutlinedTextField(
                value = form.destinatario,
                onValueChange = { actualizar(form.copy(destinatario = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Destinatario") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )
            OutlinedTextField(
                value = form.telefono,
                onValueChange = { actualizar(form.copy(telefono = it.filter(Char::isDigit).take(9))) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Teléfono del destinatario") },
                prefix = { Text("+51 ") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )
        }

        SeccionFormulario(titulo = "Origen y destino", icono = Icons.Default.Map) {
            // Uno debajo del otro: nombres como "Puerto Maldonado (Madre de Dios)" no caben en media fila.
            SelectorDesplegable(
                etiqueta = "Origen",
                opciones = RutaRepository.ciudades,
                seleccion = form.origen,
                texto = { Region.deCiudad(it).nombreCompleto },
                onSeleccion = { actualizar(form.copy(origen = it)) },
                icono = Icons.Default.TripOrigin
            )
            SelectorDesplegable(
                etiqueta = "Destino (25 regiones)",
                opciones = RutaRepository.ciudades,
                seleccion = form.destino,
                texto = { Region.deCiudad(it).nombreCompleto },
                onSeleccion = { actualizar(form.copy(destino = it)) },
                icono = Icons.Default.Place
            )
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        val regionDestino = Region.deCiudad(form.destino)
                        val viaHub = ruta.origen != form.origen && ruta.destino != form.origen
                        Text(
                            text = "Ruta asignada: ${ruta.codigo} · ${regionDestino.etiqueta} (${regionDestino.zona.etiqueta})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = buildString {
                                append("${ruta.distanciaKm} km · ~${ruta.horasViaje} h · ${ruta.modalidad}")
                                if (viaHub) append(" · vía hub ${ruta.origen}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        SeccionFormulario(titulo = "Carga", icono = Icons.Default.Inventory) {
            OutlinedTextField(
                value = form.peso,
                onValueChange = { valor ->
                    actualizar(form.copy(peso = valor.filter { it.isDigit() || it == '.' }.take(8)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Peso") },
                suffix = { Text("kg") },
                leadingIcon = { Icon(Icons.Default.Scale, contentDescription = null) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                singleLine = true
            )
            OutlinedTextField(
                value = form.descripcion,
                onValueChange = { actualizar(form.copy(descripcion = it.take(80))) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Descripción del contenido") },
                leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                maxLines = 3
            )
            Text(
                text = "Tipo de servicio",
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SERVICIO_ESTANDAR, SERVICIO_EXPRESS).forEach { servicio ->
                    FilterChip(
                        selected = form.servicio == servicio,
                        onClick = { actualizar(form.copy(servicio = servicio)) },
                        label = {
                            Text(if (servicio == SERVICIO_EXPRESS) "Express (llega 4 h antes)" else "Estándar")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (servicio == SERVICIO_EXPRESS) Icons.Default.Bolt else Icons.Default.LocalShipping,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }

        AnimatedVisibility(visible = error != null) {
            MensajeError(texto = error ?: "")
        }

        Button(
            onClick = {
                val mensaje = onValidar(form)
                if (mensaje != null) {
                    error = mensaje
                } else {
                    enviando = true
                    scope.launch {
                        delay(700)
                        registrado = onRegistrar(form)
                        enviando = false
                    }
                }
            },
            enabled = !enviando,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (enviando) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text("Generando guía…")
            } else {
                Icon(Icons.Default.ConfirmationNumber, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registrar y generar guía")
            }
        }
    }

    registrado?.let { envio ->
        val reiniciar = {
            registrado = null
            form = NuevoEnvioForm(remitente = remitente)
        }
        AlertDialog(
            onDismissRequest = {},
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            title = { Text("¡Envío registrado!") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tu número de guía es:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = envio.guia.numero,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${envio.origen} → ${envio.destino} · Llegada estimada ${envio.eta.corto()}",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val guia = envio.guia.numero
                        reiniciar()
                        onVerSeguimiento(guia)
                    }
                ) { Text("Ver seguimiento") }
            },
            dismissButton = {
                TextButton(onClick = reiniciar) { Text("Registrar otro") }
            }
        )
    }
}

@Composable
fun SeccionFormulario(
    titulo: String,
    icono: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icono, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            content()
        }
    }
}

/* ============================================================
   11. CLIENTE: BUSCAR GUÍA (RF07)
   ============================================================ */

@Composable
fun BuscarGuiaScreen(
    error: String?,
    recientes: List<String>,
    sugerencias: List<String>,
    onBuscar: (String) -> Unit,
    onLimpiarError: () -> Unit
) {
    var numero by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BannerDegradado {
            Icon(
                imageVector = Icons.Default.TravelExplore,
                contentDescription = null,
                tint = RojoRuta,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Rastrea tu carga",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ingresa el número de guía que recibiste al registrar tu envío.",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        OutlinedTextField(
            value = numero,
            onValueChange = {
                numero = it.uppercase()
                onLimpiarError()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Número de guía") },
            placeholder = { Text("RLP-26-100231-7") },
            leadingIcon = { Icon(Icons.Default.ConfirmationNumber, contentDescription = null) },
            trailingIcon = {
                if (numero.isNotEmpty()) {
                    IconButton(onClick = { numero = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Borrar")
                    }
                }
            },
            supportingText = { Text(error ?: "Formato: RLP-AA-NNNNNN-D (los guiones son opcionales)") },
            isError = error != null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(onSearch = { onBuscar(numero) }),
            singleLine = true
        )

        Button(
            onClick = { onBuscar(numero) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Buscar envío")
        }

        if (recientes.isNotEmpty()) {
            ListaChipsGuia(
                titulo = "Búsquedas recientes",
                icono = Icons.Default.History,
                guias = recientes,
                onClick = { guia ->
                    numero = guia
                    onBuscar(guia)
                }
            )
        }

        if (sugerencias.isNotEmpty()) {
            ListaChipsGuia(
                titulo = "Tus envíos en curso",
                icono = Icons.Default.LocalShipping,
                guias = sugerencias,
                onClick = { guia ->
                    numero = guia
                    onBuscar(guia)
                }
            )
        }
    }
}

@Composable
fun ListaChipsGuia(
    titulo: String,
    icono: ImageVector,
    guias: List<String>,
    onClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(guias) { guia ->
                SuggestionChip(
                    onClick = { onClick(guia) },
                    label = { Text(guia, fontFamily = FontFamily.Monospace) },
                    icon = { Icon(icono, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }
}

/* ============================================================
   12. SEGUIMIENTO + LÍNEA DE TIEMPO (RF08)
   ============================================================ */

@Composable
fun SeguimientoScreen(
    envio: Envio?,
    onBuscarOtra: () -> Unit
) {
    if (envio == null) {
        EstadoVacio(
            icono = Icons.Default.Search,
            titulo = "Sin guía seleccionada",
            mensaje = "Busca un número de guía para ver el seguimiento de tu carga.",
            accion = "Buscar guía",
            onAccion = onBuscarOtra
        )
        return
    }

    val ruta = RutaRepository.ruta(envio.rutaId)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { CabeceraSeguimiento(envio = envio, ruta = ruta) }
        item { TarjetaLineaDeTiempo(envio = envio) }
        item { DetalleEnvioCard(envio = envio, ruta = ruta) }
        item {
            OutlinedButton(
                onClick = onBuscarOtra,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buscar otra guía")
            }
        }
    }
}

@Composable
fun CabeceraSeguimiento(envio: Envio, ruta: Ruta) {
    BannerDegradado {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "N° de guía",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = envio.guia.numero,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(color = Color.White, shape = CircleShape) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = envio.estado.icono,
                        contentDescription = null,
                        tint = envio.estado.color,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = envio.estado.etiqueta,
                        color = envio.estado.color,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(envio.origen, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Origen", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                Text(envio.destino, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Destino", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            }
        }

        BarraRutaAnimada(
            progreso = progresoViaje(envio),
            colorBase = Color.White.copy(alpha = 0.25f),
            colorAvance = RojoRuta
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (envio.estado == EstadoEnvio.ENTREGADO) {
                    "Entregado el ${envio.eta.corto()}"
                } else {
                    "Llegada estimada: ${envio.eta.corto()} (en ${tiempoRestante(envio.eta)})"
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = "Ruta ${ruta.codigo} · ${ruta.distanciaKm} km · Servicio ${envio.servicio}",
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun BarraRutaAnimada(
    progreso: Float,
    colorBase: Color,
    colorAvance: Color,
    modifier: Modifier = Modifier
) {
    val animado = remember { Animatable(0f) }
    LaunchedEffect(progreso) {
        animado.animateTo(progreso, tween(1000, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
    ) {
        val ancho = maxWidth
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(colorBase)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(ancho * animado.value)
                .height(4.dp)
                .clip(CircleShape)
                .background(colorAvance)
        )
        Icon(
            imageVector = Icons.Default.LocalShipping,
            contentDescription = null,
            tint = colorAvance,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (ancho - 24.dp) * animado.value)
                .size(24.dp)
        )
    }
}

@Composable
fun TarjetaLineaDeTiempo(envio: Envio) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Línea de tiempo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Última actualización: ${envio.eventos.last().fechaHora.fechaHora()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            LineaDeTiempo(envio = envio)
        }
    }
}

private val pasosLineaTiempo = listOf(
    EstadoEnvio.RECOGIDO,
    EstadoEnvio.EN_TRANSITO,
    EstadoEnvio.EN_REPARTO,
    EstadoEnvio.ENTREGADO
)

@Composable
fun LineaDeTiempo(envio: Envio) {
    val registro = envio.eventos.firstOrNull { it.estado == EstadoEnvio.PENDIENTE_RECOJO }

    Column {
        if (registro != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ConfirmationNumber,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Guía emitida el ${registro.fechaHora.fechaHora()} · ${registro.ubicacion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        pasosLineaTiempo.forEachIndexed { indice, paso ->
            PasoLineaTiempo(
                paso = paso,
                evento = envio.eventos.lastOrNull { it.estado == paso },
                completado = envio.estado.paso >= paso.paso,
                actual = envio.estado == paso,
                lineaCompleta = envio.estado.paso > paso.paso,
                esUltimo = indice == pasosLineaTiempo.lastIndex
            )
        }
    }
}

@Composable
fun PasoLineaTiempo(
    paso: EstadoEnvio,
    evento: EventoSeguimiento?,
    completado: Boolean,
    actual: Boolean,
    lineaCompleta: Boolean,
    esUltimo: Boolean
) {
    val colorInactivo = MaterialTheme.colorScheme.outlineVariant
    val pulso = rememberInfiniteTransition(label = "PulsoPaso")
    val escala by pulso.animateFloat(
        initialValue = 1f,
        targetValue = if (actual) 1.35f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "EscalaPaso"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
        ) {
            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (actual && paso != EstadoEnvio.ENTREGADO) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .scale(escala)
                            .clip(CircleShape)
                            .background(paso.color.copy(alpha = 0.25f))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (completado) paso.color else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (completado && !actual) Icons.Default.Check else paso.icono,
                        contentDescription = null,
                        tint = if (completado) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (!esUltimo) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (lineaCompleta) paso.color else colorInactivo)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 6.dp, bottom = if (esUltimo) 0.dp else 22.dp)
        ) {
            Text(
                text = paso.etiqueta,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (actual) FontWeight.Bold else FontWeight.SemiBold,
                color = if (completado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (evento != null) {
                Text(
                    text = evento.descripcion,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "${evento.fechaHora.fechaHora()} · ${evento.ubicacion}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Pendiente",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DetalleEnvioCard(envio: Envio, ruta: Ruta) {
    val transportista = RutaRepository.transportista(envio.transportistaId)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Datos del envío",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            FilaDato(Icons.Default.Business, "Remitente", envio.remitente)
            FilaDato(Icons.Default.Person, "Destinatario", "${envio.destinatario} · ${formatoTelefono(envio.telefonoDestinatario)}")
            FilaDato(Icons.Default.Description, "Contenido", envio.descripcion)
            FilaDato(Icons.Default.Scale, "Peso", formatoPeso(envio.pesoKg))
            FilaDato(
                Icons.Default.Map,
                "Ruta",
                "${ruta.codigo} · ${envio.origen} → ${envio.destino} · Región ${envio.region.etiqueta} · ${ruta.modalidad}"
            )
            FilaDato(
                Icons.Default.DirectionsCar,
                "Transportista",
                transportista?.let { "${it.nombre} · ${it.placa} · ${it.vehiculo}" } ?: "Por asignar"
            )
        }
    }
}

@Composable
fun FilaDato(icono: ImageVector, etiqueta: String, valor: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = etiqueta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = valor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/* ============================================================
   13. LISTA DE ENVÍOS CON FILTROS (RF03 + RF04)
   ============================================================ */

@Composable
fun EnvioListScreen(
    envios: List<Envio>,
    filtros: FiltrosEnvio,
    onEstado: (EstadoEnvio?) -> Unit,
    onZona: (Zona?) -> Unit,
    onRegion: (Region?) -> Unit,
    onTexto: (String) -> Unit,
    onLimpiar: () -> Unit,
    onEnvio: (Envio) -> Unit
) {
    val texto = filtros.texto
    val filtroEstado = filtros.estado
    val regionesDeZona = Region.entries.filter { filtros.zona == null || it.zona == filtros.zona }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            OutlinedTextField(
                value = texto,
                onValueChange = onTexto,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                placeholder = { Text("Buscar por guía, destinatario, ciudad o región") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (texto.isNotEmpty()) {
                        IconButton(onClick = { onTexto("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )
        }

        item {
            EtiquetaFiltro("Estado")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filtroEstado == null,
                        onClick = { onEstado(null) },
                        label = { Text("Todos") }
                    )
                }
                items(EstadoEnvio.entries) { estado ->
                    FilterChip(
                        selected = filtroEstado == estado,
                        onClick = { onEstado(if (filtroEstado == estado) null else estado) },
                        label = { Text(estado.etiqueta) },
                        leadingIcon = {
                            Icon(
                                imageVector = estado.icono,
                                contentDescription = null,
                                tint = estado.color,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        item {
            EtiquetaFiltro("Zona del país")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filtros.zona == null,
                        onClick = { onZona(null) },
                        label = { Text("Todo el Perú") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
                items(Zona.entries) { zona ->
                    FilterChip(
                        selected = filtros.zona == zona,
                        onClick = { onZona(if (filtros.zona == zona) null else zona) },
                        label = { Text(zona.etiqueta) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        item {
            SelectorDesplegable(
                etiqueta = "Región de destino",
                opciones = listOf<Region?>(null) + regionesDeZona,
                seleccion = filtros.region,
                texto = { region ->
                    region?.nombreCompleto
                        ?: if (filtros.zona == null) {
                            "Las 25 regiones"
                        } else {
                            "Todas las regiones (${filtros.zona.etiqueta})"
                        }
                },
                onSeleccion = onRegion,
                icono = Icons.Default.Map,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${envios.size} ${if (envios.size == 1) "envío" else "envíos"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (filtros.activos) {
                    TextButton(onClick = onLimpiar) {
                        Icon(Icons.Default.FilterAltOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpiar filtros")
                    }
                }
            }
        }

        if (envios.isEmpty()) {
            item {
                EstadoVacio(
                    icono = Icons.Default.Inventory,
                    titulo = "Sin resultados",
                    mensaje = "No hay envíos que coincidan con los filtros seleccionados.",
                    accion = "Limpiar filtros",
                    onAccion = onLimpiar
                )
            }
        }

        items(envios, key = { it.id }) { envio ->
            EnvioCard(
                envio = envio,
                onClick = { onEnvio(envio) },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .animateItem()
            )
        }
    }
}

@Composable
fun EtiquetaFiltro(texto: String) {
    Text(
        text = texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun EnvioCard(
    envio: Envio,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = envio.guia.numero,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = envio.descripcion,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                EstadoBadge(estado = envio.estado)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${envio.origen} → ${envio.destino}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                RegionBadge(region = envio.region)
            }

            LinearProgressIndicator(
                progress = { envio.estado.paso / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = envio.estado.color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row {
                Text(
                    text = "Para: ${envio.destinatario}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = textoEta(envio),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EstadoBadge(estado: EstadoEnvio) {
    Surface(
        color = estado.color.copy(alpha = 0.14f),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = estado.icono,
                contentDescription = null,
                tint = estado.color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = estado.etiqueta,
                color = estado.color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun RegionBadge(region: Region) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = region.etiqueta,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/* ============================================================
   14. OPERADOR: DASHBOARD
   ============================================================ */

@Composable
fun OperadorDashboardScreen(
    resumen: ResumenOperacion,
    rutasActivas: List<RutaActiva>,
    pendientes: List<Envio>,
    onVerEstado: (EstadoEnvio?) -> Unit,
    onVerRutas: () -> Unit,
    onEnvio: (Envio) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BannerDegradado {
                Text(
                    text = "Operación nacional",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    NumeroAnimado(
                        valor = resumen.total,
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "envíos registrados",
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    EstadisticaBanner(rutasActivas.size, "Rutas activas", Modifier.weight(1f))
                    EstadisticaBanner(resumen.entregadosHoy, "Entregados hoy", Modifier.weight(1f))
                    EstadisticaBanner(resumen.sinTransportista, "Sin asignar", Modifier.weight(1f))
                }
            }
        }

        item {
            Text(
                text = "Estados de la operación",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EstadoEnvio.entries.chunked(2).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        fila.forEach { estado ->
                            KpiCard(
                                titulo = estado.etiqueta,
                                valor = resumen.porEstado[estado] ?: 0,
                                icono = estado.icono,
                                color = estado.color,
                                onClick = { onVerEstado(estado) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (fila.size == 1) {
                            KpiCard(
                                titulo = "Todos los envíos",
                                valor = resumen.total,
                                icono = Icons.Default.Inventory,
                                color = MaterialTheme.colorScheme.primary,
                                onClick = { onVerEstado(null) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        item { DistribucionEstadosCard(resumen = resumen) }

        item { CoberturaNacionalCard(resumen = resumen) }

        item {
            TituloSeccion(
                titulo = "Rutas activas",
                accion = "Ver todas",
                onAccion = onVerRutas
            )
        }

        items(rutasActivas.take(3), key = { it.ruta.id }) { activa ->
            RutaActivaCard(
                activa = activa,
                compacta = true,
                onClick = onVerRutas,
                onEnvio = onEnvio
            )
        }

        item {
            TituloSeccion(titulo = "Requieren transportista (${pendientes.size})")
        }

        if (pendientes.isEmpty()) {
            item {
                Text(
                    text = "Todos los envíos tienen transportista asignado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(pendientes.take(4), key = { "pendiente-${it.id}" }) { envio ->
            EnvioCard(envio = envio, onClick = { onEnvio(envio) })
        }
    }
}

@Composable
fun KpiCard(
    titulo: String,
    valor: Int,
    icono: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                NumeroAnimado(
                    valor = valor,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DistribucionEstadosCard(resumen: ResumenOperacion) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Distribución por estado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            EstadoEnvio.entries.forEach { estado ->
                val cantidad = resumen.porEstado[estado] ?: 0
                val fraccion = if (resumen.total == 0) 0f else cantidad.toFloat() / resumen.total
                val animado by animateFloatAsState(
                    targetValue = fraccion,
                    animationSpec = tween(800),
                    label = "Distribucion${estado.name}"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = estado.etiqueta,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(120.dp)
                    )
                    LinearProgressIndicator(
                        progress = { animado },
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(CircleShape),
                        color = estado.color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = cantidad.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CoberturaNacionalCard(resumen: ResumenOperacion) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cobertura nacional",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${resumen.regionesAtendidas.size} de ${Region.entries.size} regiones con envíos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Zona.entries.forEach { zona ->
                val regiones = Region.entries.filter { it.zona == zona }
                val atendidas = regiones.count { it in resumen.regionesAtendidas }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = zona.etiqueta,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${resumen.activosPorZona[zona] ?: 0} en curso · $atendidas/${regiones.size} regiones",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = regiones.joinToString(" · ") { it.etiqueta },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/* ============================================================
   15. OPERADOR: DETALLE Y ACTUALIZACIÓN DE ESTADO (RF09)
   ============================================================ */

@Composable
fun EnvioDetalleScreen(
    envio: Envio?,
    transportistas: List<Transportista>,
    onGuardar: (Int, EstadoEnvio, String?) -> String?,
    onMensaje: (String) -> Unit,
    onVerRutas: () -> Unit
) {
    if (envio == null) {
        EstadoVacio(
            icono = Icons.Default.Inventory,
            titulo = "Selecciona un envío",
            mensaje = "Elige un envío de la lista para ver su detalle."
        )
        return
    }

    var estado by remember(envio.id, envio.estado) { mutableStateOf(envio.estado) }
    var transportistaId by remember(envio.id, envio.transportistaId) { mutableStateOf(envio.transportistaId) }
    var error by remember(envio.id) { mutableStateOf<String?>(null) }
    val hayCambios = estado != envio.estado || transportistaId != envio.transportistaId
    val ruta = RutaRepository.ruta(envio.rutaId)
    val siguiente = EstadoEnvio.entries.getOrNull(envio.estado.paso + 1)
    val transportistaElegido = transportistas.firstOrNull { it.id == transportistaId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { CabeceraSeguimiento(envio = envio, ruta = ruta) }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Actualizar envío",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    SelectorDesplegable(
                        etiqueta = "Estado del envío",
                        opciones = EstadoEnvio.entries,
                        seleccion = estado,
                        texto = { it.etiqueta },
                        onSeleccion = {
                            estado = it
                            error = null
                        },
                        icono = estado.icono
                    )

                    if (siguiente != null && estado == envio.estado) {
                        AssistChip(
                            onClick = {
                                estado = siguiente
                                error = null
                            },
                            label = { Text("Avanzar a: ${siguiente.etiqueta}") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }

                    SelectorDesplegable(
                        etiqueta = "Transportista asignado",
                        opciones = listOf<Transportista?>(null) + transportistas,
                        seleccion = transportistaElegido,
                        texto = { t -> t?.let { "${it.nombre} · ${it.placa}" } ?: "Sin asignar" },
                        onSeleccion = {
                            transportistaId = it?.id
                            error = null
                        },
                        icono = Icons.Default.DirectionsCar
                    )

                    if (transportistaElegido != null) {
                        Text(
                            text = "${transportistaElegido.empresa} · ${transportistaElegido.vehiculo} · Tel. ${transportistaElegido.telefono}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = error != null) {
                        MensajeError(texto = error ?: "")
                    }

                    Button(
                        onClick = {
                            val resultado = onGuardar(envio.id, estado, transportistaId)
                            if (resultado != null) {
                                error = resultado
                            } else {
                                onMensaje("Guía ${envio.guia.numero}: ${estado.etiqueta}")
                            }
                        },
                        enabled = hayCambios,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardar cambios")
                    }
                }
            }
        }

        item { TarjetaLineaDeTiempo(envio = envio) }
        item { DetalleEnvioCard(envio = envio, ruta = ruta) }

        item {
            OutlinedButton(
                onClick = onVerRutas,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Map, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver rutas activas")
            }
        }
    }
}

/* ============================================================
   16. OPERADOR: RUTAS ACTIVAS Y TIEMPO ESTIMADO (RF10)
   ============================================================ */

@Composable
fun RutasActivasScreen(
    rutasActivas: List<RutaActiva>,
    rutasSinCarga: List<Ruta>,
    onEnvio: (Envio) -> Unit
) {
    val enviosEnCurso = rutasActivas.sumOf { it.envios.size }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            BannerDegradado {
                Text(
                    text = "Monitoreo de rutas",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    EstadisticaBanner(rutasActivas.size, "Rutas activas", Modifier.weight(1f))
                    EstadisticaBanner(enviosEnCurso, "Envíos en curso", Modifier.weight(1f))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rutasActivas.firstOrNull()?.proximaLlegada?.hora() ?: "--:--",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Próxima llegada",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                val regionesConCarga = rutasActivas.flatMap { activa -> activa.envios.map { it.region } }.toSet()
                Text(
                    text = "Red nacional: ${RutaRepository.rutas.size} rutas hacia las ${Region.entries.size} regiones · " +
                        "${regionesConCarga.size} regiones con carga en curso",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (rutasActivas.isEmpty()) {
            item {
                EstadoVacio(
                    icono = Icons.Default.Map,
                    titulo = "Sin rutas activas",
                    mensaje = "No hay envíos recogidos, en tránsito ni en reparto."
                )
            }
        }

        items(rutasActivas, key = { it.ruta.id }) { activa ->
            RutaActivaCard(
                activa = activa,
                compacta = false,
                onEnvio = onEnvio
            )
        }

        if (rutasSinCarga.isNotEmpty()) {
            item {
                Text(
                    text = "Rutas sin carga en curso",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(rutasSinCarga, key = { "sin-carga-${it.id}" }) { ruta ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = ruta.codigo,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${ruta.origen} → ${ruta.destino}",
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${ruta.distanciaKm} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RutaActivaCard(
    activa: RutaActiva,
    compacta: Boolean,
    onEnvio: (Envio) -> Unit,
    onClick: () -> Unit = {}
) {
    var expandida by rememberSaveable(activa.ruta.id) { mutableStateOf(false) }
    val ruta = activa.ruta

    Card(
        onClick = { if (compacta) onClick() else expandida = !expandida },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = ruta.codigo,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                RegionBadge(region = ruta.region)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${activa.envios.size} en curso",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ruta.origen,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = ruta.destino,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }

            BarraRutaAnimada(
                progreso = activa.progreso,
                colorBase = MaterialTheme.colorScheme.surfaceVariant,
                colorAvance = MaterialTheme.colorScheme.secondary
            )

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                DatoMini(Icons.Default.Straighten, "${ruta.distanciaKm} km")
                DatoMini(Icons.Default.Schedule, "${ruta.horasViaje} h de viaje")
                DatoMini(Icons.Default.AccessTime, "ETA ${activa.proximaLlegada.hora()}")
            }

            Text(
                text = "Próxima llegada en ${tiempoRestante(activa.proximaLlegada)} (${activa.proximaLlegada.corto()})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            if (ruta.modalidad != "Terrestre") {
                DatoMini(Icons.Default.DirectionsBoat, ruta.modalidad)
            }

            activa.transportista?.let { transportista ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${transportista.nombre} · ${transportista.placa} · ${transportista.vehiculo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!compacta) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (expandida) "Ocultar envíos" else "Ver envíos de la ruta",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (expandida) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                AnimatedVisibility(visible = expandida) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        activa.envios.forEach { envio ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onEnvio(envio) }
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = envio.guia.numero,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "ETA ${envio.eta.corto()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                EstadoBadge(estado = envio.estado)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DatoMini(icono: ImageVector, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icono,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = texto,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ============================================================
   17. COMPONENTES REUTILIZABLES
   ============================================================ */

@Composable
fun <T> SelectorDesplegable(
    etiqueta: String,
    opciones: List<T>,
    seleccion: T,
    texto: (T) -> String,
    onSeleccion: (T) -> Unit,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null
) {
    var expandido by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = texto(seleccion),
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(etiqueta) },
            leadingIcon = if (icono != null) {
                { Icon(icono, contentDescription = null) }
            } else {
                null
            },
            trailingIcon = {
                Icon(
                    imageVector = if (expandido) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            },
            singleLine = true
        )
        // Capa transparente: el campo es de solo lectura y abre el menú al tocarlo.
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable { expandido = true }
        )
        DropdownMenu(
            expanded = expandido,
            onDismissRequest = { expandido = false }
        ) {
            opciones.forEach { opcion ->
                DropdownMenuItem(
                    text = { Text(texto(opcion)) },
                    onClick = {
                        onSeleccion(opcion)
                        expandido = false
                    }
                )
            }
        }
    }
}

@Composable
fun TituloSeccion(
    titulo: String,
    accion: String? = null,
    onAccion: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (accion != null) {
            TextButton(onClick = onAccion) { Text(accion) }
        }
    }
}

@Composable
fun MensajeError(texto: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = texto,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun EstadoVacio(
    icono: ImageVector,
    titulo: String,
    mensaje: String,
    accion: String? = null,
    onAccion: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icono,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = mensaje,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (accion != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onAccion) { Text(accion) }
        }
    }
}

@Composable
fun NumeroAnimado(
    valor: Int,
    color: Color,
    style: androidx.compose.ui.text.TextStyle
) {
    val animado = remember { Animatable(0f) }
    LaunchedEffect(valor) {
        animado.animateTo(valor.toFloat(), tween(800, easing = FastOutSlowInEasing))
    }
    Text(
        text = animado.value.roundToInt().toString(),
        color = color,
        style = style,
        fontWeight = FontWeight.Bold
    )
}

/* ============================================================
   18. UTILIDADES
   ============================================================ */

private val localePeru: Locale = Locale.forLanguageTag("es-PE")
private val formatoFechaHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", localePeru)
private val formatoCorto = DateTimeFormatter.ofPattern("dd MMM, HH:mm", localePeru)
private val formatoHora = DateTimeFormatter.ofPattern("HH:mm", localePeru)

fun LocalDateTime.fechaHora(): String = format(formatoFechaHora)
fun LocalDateTime.corto(): String = format(formatoCorto)
fun LocalDateTime.hora(): String = format(formatoHora)

fun tiempoRestante(destino: LocalDateTime): String {
    val minutos = Duration.between(LocalDateTime.now(), destino).toMinutes()
    return when {
        minutos <= 0 -> "instantes"
        minutos < 60 -> "$minutos min"
        minutos < 60 * 24 -> "${minutos / 60} h ${minutos % 60} min"
        else -> "${minutos / (60 * 24)} d ${(minutos / 60) % 24} h"
    }
}

fun textoEta(envio: Envio): String =
    if (envio.estado == EstadoEnvio.ENTREGADO) {
        "Entregado ${envio.eventos.last().fechaHora.corto()}"
    } else {
        "ETA ${envio.eta.corto()}"
    }

fun formatoPeso(kg: Double): String = String.format(localePeru, "%,.1f kg", kg)

fun formatoTelefono(numero: String): String =
    if (numero.length == 9) "${numero.substring(0, 3)} ${numero.substring(3, 6)} ${numero.substring(6)}" else numero

fun proximamente(context: Context, funcion: String) {
    Toast.makeText(context, "$funcion estará disponible en la siguiente entrega.", Toast.LENGTH_SHORT).show()
}
