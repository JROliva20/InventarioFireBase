package com.example.inventariofirebase;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReporteActivity extends AppCompatActivity {

    private TextView tvTotalProd, tvTotalUnid, tvTotalValor;
    private EditText etBuscarReporte;
    private ListView lvReporte;

    private DatabaseReference ref;
    private final List<Producto> listaProductos = new ArrayList<>();
    private final List<String> listaTextosReporte = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reporte);

        tvTotalProd = findViewById(R.id.tvTotalProd);
        tvTotalUnid = findViewById(R.id.tvTotalUnid);
        tvTotalValor = findViewById(R.id.tvTotalValor);
        etBuscarReporte = findViewById(R.id.etBuscarReporte);
        lvReporte = findViewById(R.id.lvReporte);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, listaTextosReporte);
        lvReporte.setAdapter(adapter);

        ref = FirebaseDatabase.getInstance().getReference("productos");

        cargarDatosReporte();

        etBuscarReporte.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filtrarProductos(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void cargarDatosReporte() {
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listaProductos.clear();
                int totalProd = 0;
                int totalUnid = 0;
                double valorTotal = 0;

                for (DataSnapshot hijo : snapshot.getChildren()) {
                    Producto p = hijo.getValue(Producto.class);
                    if (p != null) {
                        p.setId(hijo.getKey());
                        listaProductos.add(p);

                        totalProd++;
                        totalUnid += p.getCantidad();
                        valorTotal += (p.getPrecio() * p.getCantidad());
                    }
                }

                tvTotalProd.setText(String.valueOf(totalProd));
                tvTotalUnid.setText(String.valueOf(totalUnid));

                if (valorTotal >= 1000) {
                    tvTotalValor.setText(String.format(Locale.US, "Q%.1fk", valorTotal / 1000.0));
                } else {
                    tvTotalValor.setText(String.format(Locale.US, "Q%.2f", valorTotal));
                }

                actualizarListaVisual(listaProductos);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void actualizarListaVisual(List<Producto> productos) {
        listaTextosReporte.clear();
        for (Producto p : productos) {
            String texto = p.getNombre() + " — Q" + String.format(Locale.US, "%.2f", p.getPrecio());
            if (p.getCantidad() <= p.getStockMinimo()) {
                texto = "⚠️ " + p.getNombre() + " — " + p.getCantidad() + " u. (Stock bajo)";
            }
            listaTextosReporte.add(texto);
        }
        adapter.notifyDataSetChanged();
    }

    private void filtrarProductos(String textoFiltro) {
        List<Producto> filtrados = new ArrayList<>();
        for (Producto p : listaProductos) {
            if (p.getNombre() != null && p.getNombre().toLowerCase().contains(textoFiltro.toLowerCase())) {
                filtrados.add(p);
            }
        }
        actualizarListaVisual(filtrados);
    }
}