package com.example.policia

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class NewActivity : BottomSheetDialogFragment() {

    private var placa: String? = null
    private var conductor: String? = null
    private var origen: String? = null
    private var destino: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            placa = it.getString("placa")
            conductor = it.getString("conductor")
            origen = it.getString("origen")
            destino = it.getString("destino")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_bottom_sheet, container, false)

        view.findViewById<TextView>(R.id.titleTextView).text = "$conductor"
        view.findViewById<TextView>(R.id.snippetTextView).text = "$placa\nOrigen: $origen\nDestino: $destino"

        return view
    }
}
