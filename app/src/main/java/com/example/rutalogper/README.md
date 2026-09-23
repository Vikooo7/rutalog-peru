# RutaLog Perú

**Sector:** Logística y transporte de carga
**Alcance:** Nacional (las 25 regiones del Perú)
**Tecnología:** Kotlin + Jetpack Compose + Material 3 · MVVM · un solo módulo (`:app`), un solo Activity (`MainActivity.kt`)

---

## 1. Problema que resuelve

### 

> Los remitentes no saben dónde está su carga y los operadores gestionan envíos y rutas de todo el Perú sin una vista central. RutaLog permite registrar, rastrear por número de guía y controlar las rutas activas en las 25 regiones.

### 

En el Perú, la carga que sale de Lima hacia las 25 regiones recorre rutas largas y muy distintas: costa, sierra y selva. Hay destinos, como Iquitos, a los que solo se llega combinando carretera y río. Muchas empresas pequeñas y medianas de transporte siguen coordinando sus envíos por llamadas, WhatsApp y hojas de cálculo.

**Para el cliente remitente:**
- Cuando entrega su carga, no sabe en qué etapa está: si ya la recogieron, si va en camino o si ya salió a reparto.
- No tiene un número de guía para consultar por su cuenta; tiene que llamar a la agencia.
- No sabe cuándo llegará, así que no puede avisar a su destinatario.

**Para el operador logístico:**
- No tiene una vista central de todos los envíos por estado, zona o región.
- Asignar transportistas y actualizar estados es manual, y se pierde información entre áreas.
- No sabe con facilidad qué rutas están activas, cuánta carga llevan ni la hora estimada de llegada.

**En resumen:** hay poca visibilidad y trazabilidad de la carga, tanto para quien envía como para quien opera.

### Cómo lo resuelve la app

| Problema | Función en la app | RF |
|---|---|---|
| El cliente no puede registrar su envío por su cuenta | Registrar envío con número de guía automático | RF05, RF06 |
| El cliente no sabe dónde está su carga | Buscar guía y línea de tiempo: recogido → en tránsito → en reparto → entregado | RF07, RF08 |
| No se conoce la hora de llegada | Hora estimada en el seguimiento y en rutas activas | RF10 |
| El operador no tiene una vista central | Panel de operaciones y lista de envíos con filtros por estado, zona y región | RF03, RF04 |
| Asignar transportistas y actualizar estados es manual | Detalle del envío: se cambia el estado y el transportista en un paso | RF09 |
| No se sabe qué rutas están en operación | Rutas activas, con avance, transportista y cobertura de las 25 regiones | RF10 |

---

## 2. Usuarios

| Rol | Descripción | Pantalla principal |
|---|---|---|
| **Cliente remitente** | Empresa o persona que envía carga. Registra envíos y los rastrea por número de guía. | Inicio |
| **Operador logístico / administrador** | Personal de RutaLog. Actualiza estados, asigna transportistas y supervisa las rutas activas. | Panel de operaciones |

**Cuentas demo** (clave `1234`):
- Cliente: `cliente@rutalog.pe`, clave `1234`
- Operador: `operador@rutalog.pe`, clave `1234`

---

## 3. Logo, ícono y colores

- **Ícono de la app:** el emblema del logo (mapa del Perú, caja, flecha y pin) sobre fondo blanco. Es un ícono adaptativo con versión monocromática.
  Archivos: `res/mipmap-*/ic_launcher_foreground.png`, `ic_launcher_monochrome.png`
- **Logo completo:** `res/drawable-nodpi/logo_rutalog.png`, que se usa en el Splash y el Login.

| Color | Hex | Uso |
|---|---|---|
| Azul marino | `#0B2F5B` | Color primario: barra superior, cabeceras y texto de marca |
| Azul claro | `#1B4F8A` | Degradados de las cabeceras |
| Rojo | `#E3192A` | Color secundario: acentos, camión del Splash y barra de avance |
| Verde | `#2E7D32` | Estado "Entregado" y confirmaciones |

---

## 4. Flujo principal

```
Ícono de la app → Splash → Login (elige rol)
   ├─ Cliente  → Inicio → Registrar envío / Buscar guía → Seguimiento → Línea de tiempo
   └─ Operador → Panel de operaciones → Envíos → Detalle → Actualizar estado → Rutas activas
```

---

## 5. Pantallas (Jetpack Compose)

| Pantalla | Función en `MainActivity.kt` |
|---|---|
| Splash | `SplashScreen` |
| Login | `LoginScreen` (incluye `RolCard`) |
| Inicio del cliente | `ClienteInicioScreen` |
| Registrar envío | `NuevoEnvioScreen` |
| Buscar guía | `BuscarGuiaScreen` |
| Seguimiento + línea de tiempo | `SeguimientoScreen`, `LineaDeTiempo` |
| Lista de envíos | `EnvioListScreen` |
| Panel del operador | `OperadorDashboardScreen` |
| Detalle / actualizar estado | `EnvioDetalleScreen` |
| Rutas activas | `RutasActivasScreen` |

---

## 6. Estructura MVVM 

- **model:** `Envio`, `Guia`, `Ruta`, `Transportista`, `EventoSeguimiento`, `EstadoEnvio` (además de `Region`, `Zona`, `Rol`)
- **repository:** `EnvioRepository`, `RutaRepository`, `SeguimientoRepository`, con datos simulados en memoria
- **viewmodel:** `AuthViewModel`, `EnvioViewModel`, `SeguimientoViewModel`, `OperacionLogisticaViewModel`, `RutaViewModel`

---

## 7. Avance

**Hecho:** RF01 a RF10, con datos simulados:
- 50 envíos, 27 rutas, 10 transportistas y cobertura de las 25 regiones.

**Pendiente para la siguiente entrega:**
- Guardar los datos (Room o DataStore) y conectarlos a un backend real
- Mapa de rutas en tiempo real y notificaciones push
- Perfil de usuario, recuperación de contraseña y reportes del operador
- Pruebas unitarias y de interfaz
