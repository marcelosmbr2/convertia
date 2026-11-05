package com.recast.app.data.engine.office

/** Mensagens trocadas entre o app e o [OfficeService] (processo `:office`) por Messenger. */
object OfficeProtocol {
    /** Cliente → serviço: quem é você? Resposta [MSG_HELLO] com `arg1 = pid`. */
    const val MSG_HELLO = 1

    /** Cliente → serviço: converter. `arg1 = id`, dados em [KEY_INPUT], [KEY_OUTPUT], [KEY_FORMAT], [KEY_OPTIONS]. */
    const val MSG_CONVERT = 2

    /** Serviço → cliente: resultado. `arg1 = id`, `arg2 = 1` se deu certo; senão [KEY_ERROR]. */
    const val MSG_RESULT = 3

    const val KEY_INPUT = "input"
    const val KEY_OUTPUT = "output"
    const val KEY_FORMAT = "format"
    const val KEY_OPTIONS = "options"
    const val KEY_ERROR = "error"
}
