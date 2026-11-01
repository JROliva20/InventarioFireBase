package com.example.inventariofirebase;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

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

public class DetalleActivity extends AppCompatActivity {

    private TextView tvNombreProducto, tvPrecioUnidades;
    private RadioButton rbEntrada;
    private EditText etCantidadMovimiento;
    private ListView lvHistorial;

    private DatabaseReference productoRef;
    private String productoId;
    private Producto productoActual;

    private final List<String> listaHistorial = new ArrayList<>();
    private ArrayAdapter<String> adapterHistorial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalle);

        tvNombreProducto = findViewById(R.id.tvNombreProducto);
        tvPrecioUnidades = findViewById(R.id.tvPrecioUnidades);
        rbEntrada = findViewById(R.id.rbEntrada);
        etCantidadMovimiento = findViewById(R.id.etCantidadMovimiento);
        lvHistorial = findViewById(R.id.lvHistorial);

        adapterHistorial = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, listaHistorial);
        lvHistorial.setAdapter(adapterHistorial);

        productoId = getIntent().getStringExtra("PRODUCTO_ID");

        if (productoId == null) {
            Toast.makeText(this, "Error al cargar el producto", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        productoRef = FirebaseDatabase.getInstance().getReference("productos").child(productoId);

        cargarDatosProducto();

        findViewById(R.id.btnRegistrarMovimiento).setOnClickListener(v -> registrarMovimiento());
    }

    private void cargarDatosProducto() {
        productoRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                productoActual = snapshot.getValue(Producto.class);
                if (productoActual != null) {
                    productoActual.setId(snapshot.getKey());
                    tvNombreProducto.setText(productoActual.getNombre());

                    // Aquí estaba el error (se eliminó setItemText y se dejó solo el setText limpio)
                    tvPrecioUnidades.setText(String.format(Locale.US, "Q%.2f · %d unidades",
                            productoActual.getPrecio(), productoActual.getCantidad()));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DetalleActivity.this, "Error al leer datos", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void registrarMovimiento() {
        String sCantidad = etCantidadMovimiento.getText().toString().trim();
        if (sCantidad.isEmpty()) {
            etCantidadMovimiento.setError("Ingresa una cantidad");
            return;
        }

        int cantidadMov = Integer.parseInt(sCantidad);
        if (cantidadMov <= 0) {
            etCantidadMovimiento.setError("Debe ser mayor a 0");
            return;
        }

        boolean esEntrada = rbEntrada.isChecked();
        int nuevaCantidad;

        if (esEntrada) {
            nuevaCantidad = productoActual.getCantidad() + cantidadMov;
        } else {
            nuevaCantidad = productoActual.getCantidad() - cantidadMov;
            if (nuevaCantidad < 0) {
                Toast.makeText(this, "Stock insuficiente para la salida", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        productoRef.child("cantidad").setValue(nuevaCantidad).addOnSuccessListener(unused -> {
            Toast.makeText(this, "Movimiento registrado con éxito", Toast.LENGTH_SHORT).show();
            etCantidadMovimiento.setText("");
            String tipo = esEntrada ? "+" : "-";
            listaHistorial.add(0, tipo + cantidadMov + " · " + (esEntrada ? "Compra" : "Venta"));
            adapterHistorial.notifyDataSetChanged();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error al actualizar stock: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }
}