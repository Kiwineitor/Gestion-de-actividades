export const VERSION_TERMINOS = '2026-11';
export const VIGENCIA = 'noviembre de 2026';

export function terminosDeUso({ institucion, contacto }) {
  return {
    titulo: 'Términos de uso del sistema',
    subtitulo: `Para el personal autorizado de ${institucion}`,
    secciones: [
      { titulo: '1. Aceptación', parrafos: [
        `Este sistema es una herramienta de ${institucion} para organizar actividades, vender espacios y entradas, validar pagos, emitir credenciales y controlar el ingreso. Al entrar con tu usuario aceptas estos términos. Si no estás de acuerdo, no uses el sistema y avisa a tu responsable.`,
      ] },
      { titulo: '2. Tu cuenta es personal e intransferible', lista: [
        'Tu usuario y contraseña son solo tuyos: no los prestes, no los escribas en lugares visibles ni los compartas por mensaje.',
        'Al primer ingreso debes cambiar la contraseña inicial por una propia de al menos 10 caracteres, con letras y números.',
        'Eres responsable de todo lo que se haga con tu cuenta. Cierra sesión al terminar, sobre todo en equipos compartidos.',
        'Si sospechas que alguien conoce tu contraseña, cámbiala de inmediato desde «Cambiar mi contraseña» y avisa a tu responsable.',
        'Después de varios intentos fallidos el ingreso se pausa unos minutos en ese equipo por seguridad.',
        'Al crear tu contraseña recibes un código de recuperación: guárdalo en un lugar seguro y no lo compartas. Sirve para crear una contraseña nueva si la olvidas.',
      ] },
      { titulo: '3. Uso permitido', parrafos: [
        'Usa el sistema solo para las tareas que te asignaron según tu rol: vender, validar pagos, entregar entradas o credenciales, controlar el ingreso, diseñar actividades o administrar, según corresponda.',
      ] },
      { titulo: '4. Está prohibido', lista: [
        'Modificar datos directamente en la base de datos, en archivos del servidor o por cualquier vía distinta del propio sistema, incluido reemplazar contraseñas o roles. El sistema lo detecta y bloquea la cuenta afectada.',
        'Intentar entrar a pantallas, datos o funciones que tu rol no permite, probar fallas de seguridad o usar programas automáticos contra el sistema.',
        'Registrar ventas, pagos o personas falsas, aprobar un pago sin verificar su comprobante, alterar comprobantes o cobrar montos distintos a los que muestra el sistema.',
        'Anular ventas sin un motivo real o para ocultar dinero cobrado.',
        'Copiar, descargar, fotografiar o compartir datos de clientes (nombres, C.I., celulares, comprobantes, fotos) para fines ajenos a la actividad.',
        'Usar los números de WhatsApp de los clientes para publicidad propia, de terceros o para asuntos personales.',
        'Subir imágenes o textos ofensivos, ilícitos o que infrinjan derechos de terceros (planos, fondos, anuncios, diseños de credenciales y entradas).',
      ] },
      { titulo: '5. Datos personales de los clientes', parrafos: [
        'Los datos que ves son confidenciales. Úsalos solo para atender la compra, validar el pago, entregar entradas o credenciales y controlar el ingreso. Borra de tu equipo los archivos que descargues (recibos, listas, reportes) cuando ya no los necesites.',
      ] },
      { titulo: '6. Registro de acciones', parrafos: [
        'El sistema guarda en el «Historial de acciones» lo que hace cada usuario: ingresos, ventas, aprobaciones, anulaciones, cambios de precios y de permisos, con fecha, hora y dirección de conexión. Este registro sirve para auditoría y para resolver reclamos.',
      ] },
      { titulo: '7. Dinero y rendición de cuentas', parrafos: [
        'Cada venta queda a nombre de quien la registró o aprobó. Debes rendir cuentas del dinero que recibes según «Detalle de ventas › Rendición de cuentas». Toda anulación requiere un motivo y queda registrada.',
      ] },
      { titulo: '8. Responsabilidades', parrafos: [
        `${institucion} es responsable de la información que publica, de los precios, de los cobros y de la relación con sus clientes. El proveedor del sistema entrega y mantiene la herramienta, pero no responde por el mal uso de las cuentas, por contraseñas compartidas, por datos cargados de forma incorrecta ni por decisiones comerciales de la organización.`,
        'Tampoco responde por fallas de servicios externos como internet, bancos, aplicaciones de pago o WhatsApp. El sistema puede entrar en mantenimiento programado; se avisará con anticipación siempre que sea posible.',
      ] },
      { titulo: '9. Incumplimiento', parrafos: [
        `El incumplimiento de estos términos puede llevar a la suspensión inmediata de la cuenta, al aviso a ${institucion} y, si corresponde, a las acciones legales que procedan.`,
      ] },
      { titulo: '10. Cambios y contacto', parrafos: [
        `Si estos términos cambian, el sistema te pedirá aceptarlos de nuevo. Versión vigente: ${VIGENCIA}.${contacto ? ` Consultas: ${contacto}.` : ''}`,
      ] },
    ],
  };
}

export function terminosDeCompra({ institucion, contacto }) {
  return {
    titulo: 'Términos y condiciones de compra',
    subtitulo: `Compras de espacios y entradas en las actividades de ${institucion}`,
    secciones: [
      { titulo: '1. Quién vende', parrafos: [
        `Las actividades, los espacios (mesas, puestos, stands) y las entradas los ofrece ${institucion}, que organiza cada actividad. Esta página es el medio para solicitarlos y pagarlos. Cada actividad puede tener condiciones propias, que se muestran antes de comprar y se aplican junto con estas.`,
      ] },
      { titulo: '2. Cómo funciona la compra', lista: [
        'Eliges el espacio o las entradas, completas tus datos y pagas con QR o transferencia adjuntando el comprobante, o en persona si la actividad lo permite.',
        'Cuando eliges un espacio te lo guardamos unos minutos para que completes la compra. Si el tiempo se acaba, se libera para otras personas.',
        'Tu compra queda «en revisión» hasta que el personal verifique el pago. Recién entonces queda confirmada y recibes por WhatsApp tu recibo y, si corresponde, tus entradas o credenciales.',
        'Antes de enviar te pedimos confirmar tu número de WhatsApp: si está mal escrito no podremos contactarte. Puedes dejar un segundo número opcional.',
      ] },
      { titulo: '3. Precios y pagos', lista: [
        'Los precios están en bolivianos (Bs) y son los que muestra la página al momento de comprar.',
        'Paga el monto exacto en la cuenta o QR indicados por la organización. Los pagos a otras cuentas no se reconocen.',
        'El comprobante debe ser verdadero y legible. Un comprobante falso, alterado o de otra compra se rechaza, se libera el espacio y puede ser denunciado.',
        'Si tu pago no se puede verificar, te lo indicaremos con el motivo para que lo corrijas.',
      ] },
      { titulo: '4. Entradas digitales y credenciales', lista: [
        'Cada entrada tiene un código QR único que se verifica en la puerta. Cada QR vale por una persona y no se puede usar para entrar otra vez mientras esa persona esté dentro.',
        'Cuida tus entradas: quien muestre primero el QR es quien entra. No compartas su imagen en redes sociales.',
        'Está prohibido revender, copiar o falsificar entradas y credenciales. Las que se detecten como falsas o duplicadas se anulan sin derecho a reclamo.',
      ] },
      { titulo: '5. Espacios (mesas, puestos, stands)', lista: [
        'Te corresponde el espacio que figura en tu confirmación, con las personas incluidas según su categoría. Si la actividad lo permite, las personas extra se pagan aparte.',
        'El día de la actividad identifícate con tu nombre o C.I. en la recepción para que confirmen tu llegada y te ubiquen.',
        'Por seguridad o fuerza mayor la organización puede reubicarte en un espacio equivalente, avisándote.',
      ] },
      { titulo: '6. Cambios, posposición y cancelación', parrafos: [
        'Si una actividad se pospone o se cancela, la organización lo comunicará en esta página y por WhatsApp. Las devoluciones o cambios se rigen por las condiciones de cada actividad y por la política de la organización.',
      ] },
      { titulo: '7. Tu compromiso', lista: [
        'Proporcionar datos verdaderos y propios.',
        'No usar la página para fines ilícitos ni intentar alterar su funcionamiento.',
        'Respetar las normas del lugar y las indicaciones del personal durante la actividad.',
        'Las compras deben realizarlas personas mayores de edad o con autorización de su padre, madre o tutor.',
      ] },
      { titulo: '8. Contacto', parrafos: [
        `Para consultas o reclamos sobre tu compra escribe a ${institucion}${contacto ? ` (${contacto})` : ''} indicando tu código de compra. Versión vigente: ${VIGENCIA}.`,
      ] },
    ],
  };
}

export function politicaDePrivacidad({ institucion, contacto }) {
  return {
    titulo: 'Política de privacidad',
    subtitulo: 'Qué datos pedimos, para qué y cómo los cuidamos',
    secciones: [
      { titulo: '1. Responsable de tus datos', parrafos: [
        `${institucion} es responsable de los datos que ingresas al comprar o reservar en sus actividades${contacto ? ` (contacto: ${contacto})` : ''}.`,
      ] },
      { titulo: '2. Qué datos recopilamos', lista: [
        'Nombre completo y número de carnet (C.I.).',
        'Número de celular con WhatsApp y, si lo das, un segundo número de contacto.',
        'Nombre de tu negocio y rubro, cuando solicitas un puesto o stand.',
        'El comprobante de pago que adjuntas.',
        'La foto para tu credencial, solo si la actividad la pide.',
        'La fecha y hora de tus ingresos y salidas cuando se escanea tu entrada o credencial.',
        'Datos técnicos mínimos de seguridad (fecha, hora y dirección de conexión de cada solicitud).',
      ] },
      { titulo: '3. Para qué los usamos', lista: [
        'Registrar y gestionar tu compra o reserva y verificar tu pago.',
        'Emitir tu recibo, tus entradas y tus credenciales.',
        'Comunicarnos contigo por WhatsApp sobre tu compra y sobre cambios o avisos importantes de la actividad.',
        'Controlar el ingreso y la seguridad del evento, y prevenir fraudes.',
        'Rendir cuentas internamente de lo vendido y cobrado.',
      ] },
      { titulo: '4. Lo que no hacemos', lista: [
        'No vendemos, alquilamos ni regalamos tus datos.',
        'No los publicamos. En la página pública solo se ve si un espacio está libre u ocupado, nunca quién lo compró.',
        'No los usamos para publicidad de terceros.',
      ] },
      { titulo: '5. Con quién se comparten', parrafos: [
        `Solo con el personal autorizado de ${institucion}, que tiene cuentas personales y deja registro de lo que hace; con el proveedor técnico que aloja y mantiene el sistema, bajo deber de confidencialidad; con el servicio de mensajería usado para enviarte mensajes por WhatsApp; y con las autoridades cuando la ley lo exija.`,
      ] },
      { titulo: '6. Cuánto tiempo los guardamos', parrafos: [
        'Mientras dure la actividad y el tiempo necesario para la rendición de cuentas y las obligaciones legales y contables, que en general no supera los dos años. Después se eliminan o se anonimizan.',
      ] },
      { titulo: '7. Cómo los protegemos', parrafos: [
        'El acceso es solo para personal autorizado con usuario y contraseña propios. Las contraseñas se guardan cifradas, las cuentas tienen un sello de integridad que impide alterarlas desde fuera del sistema y todas las acciones quedan registradas.',
      ] },
      { titulo: '8. Tus derechos', parrafos: [
        `Puedes pedir conocer los datos que tenemos de ti, corregirlos o actualizarlos, y pedir que se eliminen cuando ya no sean necesarios, salvo los que debamos conservar por ley o por rendición de cuentas. También puedes pedir que no te enviemos avisos de nuevas actividades. Escríbenos${contacto ? ` a ${contacto}` : ''} indicando tu nombre y tu código de compra. Estos derechos se reconocen conforme a la Constitución Política del Estado Plurinacional de Bolivia (artículos 21 y 130) y la normativa vigente.`,
      ] },
      { titulo: '9. Cambios a esta política', parrafos: [
        `Publicaremos aquí cualquier cambio. Versión vigente: ${VIGENCIA}.`,
      ] },
    ],
  };
}
