package com.example.inventariofirebase;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "InventarioFirebase";

    private EditText etNombre, etDescripcion, etPrecio, etCantidad, etStockMinimo;
    private DatabaseReference ref;
    private ValueEventListener listener;

    private final List<Producto> producto = new ArrayList<>();
    private final List<String> textos = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private Producto seleccionado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Inicializar vistas
        etNombre = findViewById(R.id.etNombre);
        etDescripcion = findViewById(R.id.etDescripcion);
        etPrecio = findViewById(R.id.etPrecio);
        etCantidad = findViewById(R.id.etCantidad);
        etStockMinimo = findViewById(R.id.etStockMinimo);

        ListView lvProductos = findViewById(R.id.lvProductos);
        ref = FirebaseDatabase.getInstance().getReference("productos");

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, textos);
        lvProductos.setAdapter(adapter);

        // Listeners de botones
        findViewById(R.id.btnGuardar).setOnClickListener(v -> guardar());
        findViewById(R.id.btnActualizar).setOnClickListener(v -> actualizar());
        findViewById(R.id.btnEliminar).setOnClickListener(v -> confirmarEliminar());

        lvProductos.setOnItemClickListener((parent, view, pos, id) ->
                seleccionar(producto.get(pos)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        escuchar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (listener != null) ref.removeEventListener(listener);
    }

    private Producto leerFormulario(String id, String idExcluir) {
        boolean ok = true;

        String nombre = etNombre.getText().toString().trim();
        String descripcion = etDescripcion.getText().toString().trim();
        String sPrecio = etPrecio.getText().toString().trim().replace(',', '.');
        String sCantidad = etCantidad.getText().toString().trim();
        String sStockMinimo = etStockMinimo.getText().toString().trim();

        if (nombre.isEmpty()) {
            etNombre.setError("El nombre es obligatorio");
            ok = false;
        } else if (nombre.length() < 2) {
            etNombre.setError("Mínimo 2 caracteres");
            ok = false;
        } else if (existeNombre(nombre, idExcluir)) {
            etNombre.setError("Ya existe un producto con ese nombre");
            ok = false;
        }

        double precio = 0;
        if (sPrecio.isEmpty()) {
            etPrecio.setError("Ingresa el precio");
            ok = false;
        } else {
            try {
                precio = Double.parseDouble(sPrecio);
                if (precio <= 0 || precio > 1_000_000) {
                    etPrecio.setError("El precio debe ser mayor a 0 y no superar Q1,000,000");
                    ok = false;
                }
            } catch (NumberFormatException e) {
                etPrecio.setError("Precio inválido");
                ok = false;
            }
        }

        int cantidad = 0;
        if (sCantidad.isEmpty()) {
            etCantidad.setError("Ingresa la cantidad");
            ok = false;
        } else {
            try {
                cantidad = Integer.parseInt(sCantidad);
                if (cantidad < 0) {
                    etCantidad.setError("La cantidad no puede ser negativa");
                    ok = false;
                }
            } catch (NumberFormatException e) {
                etCantidad.setError("Cantidad inválida");
                ok = false;
            }
        }

        int stockMinimo = 0;
        if (!sStockMinimo.isEmpty()) {
            try {
                stockMinimo = Integer.parseInt(sStockMinimo);
                if (stockMinimo < 0) {
                    etStockMinimo.setError("El stock mínimo no puede ser negativo");
                    ok = false;
                }
            } catch (NumberFormatException e) {
                etStockMinimo.setError("Stock mínimo inválido");
                ok = false;
            }
        }

        if (!ok) return null;

        // Orden de parámetros ajustado al constructor de Producto
        return new Producto(id, nombre, precio, cantidad, descripcion, stockMinimo);
    }

    private boolean existeNombre(String nombre, String idExcluir) {
        for (Producto p : producto) {
            if (p.getNombre() != null
                    && p.getNombre().equalsIgnoreCase(nombre)
                    && (idExcluir == null || !idExcluir.equals(p.getId()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hayProductoSeleccionado() {
        if (seleccionado == null || seleccionado.getId() == null) {
            Toast.makeText(this, "Selecciona un producto de la lista", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void guardar() {
        Producto p = leerFormulario(null, null);
        if (p == null) return;

        DatabaseReference nuevoProducto = ref.push();
        String id = nuevoProducto.getKey();

        if (id == null) {
            Toast.makeText(this, "No se pudo generar el ID del producto", Toast.LENGTH_LONG).show();
            return;
        }

        p.setId(id);

        nuevoProducto.setValue(p)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(MainActivity.this, "Producto guardado correctamente", Toast.LENGTH_SHORT).show();
                    limpiar();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "Error al guardar: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void escuchar() {
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                producto.clear();
                textos.clear();
                for (DataSnapshot hijo : snapshot.getChildren()) {
                    Producto p = hijo.getValue(Producto.class);
                    if (p != null) {
                        p.setId(hijo.getKey());
                        producto.add(p);
                        textos.add(formatear(p));
                    }
                }
                adapter.notifyDataSetChanged();

                if (seleccionado != null && !existeId(seleccionado.getId())) {
                    seleccionado = null;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Error al leer: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
        ref.orderByChild("nombre").addValueEventListener(listener);
    }

    private boolean existeId(String id) {
        for (Producto p : producto) {
            if (p.getId() != null && p.getId().equals(id)) return true;
        }
        return false;
    }

    private String formatear(Producto p) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "%s - Q%.2f (%d u.)",
                p.getNombre(), p.getPrecio(), p.getCantidad()));

        if (p.getDescripcion() != null && !p.getDescripcion().isEmpty()) {
            sb.append("\n").append(p.getDescripcion());
        }

        if (p.getStockMinimo() > 0 && p.getCantidad() <= p.getStockMinimo()) {
            sb.append("\n⚠️ Stock bajo (mínimo: ").append(p.getStockMinimo()).append(")");
        }
        return sb.toString();
    }

    private void seleccionar(Producto p) {
        seleccionado = p;
        etNombre.setText(p.getNombre());
        etDescripcion.setText(p.getDescripcion() != null ? p.getDescripcion() : "");
        etPrecio.setText(String.valueOf(p.getPrecio()));
        etCantidad.setText(String.valueOf(p.getCantidad()));
        etStockMinimo.setText(String.valueOf(p.getStockMinimo()));
    }

    private void actualizar() {
        if (!hayProductoSeleccionado()) return;

        Producto p = leerFormulario(seleccionado.getId(), seleccionado.getId());
        if (p == null) return;

        Map<String, Object> cambios = new HashMap<>();
        cambios.put("nombre", p.getNombre());
        cambios.put("descripcion", p.getDescripcion());
        cambios.put("precio", p.getPrecio());
        cambios.put("cantidad", p.getCantidad());
        cambios.put("stockMinimo", p.getStockMinimo());

        ref.child(seleccionado.getId()).updateChildren(cambios)
                .addOnSuccessListener(x -> {
                    Toast.makeText(this, "Se modificó con éxito", Toast.LENGTH_SHORT).show();
                    limpiar();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void confirmarEliminar() {
        if (!hayProductoSeleccionado()) return;

        new AlertDialog.Builder(this)
                .setTitle("Eliminar producto")
                .setMessage("¿Seguro que deseas eliminar \"" + seleccionado.getNombre() + "\"?")
                .setPositiveButton("Eliminar", (d, w) -> eliminar())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void eliminar() {
        ref.child(seleccionado.getId()).removeValue()
                .addOnSuccessListener(x -> {
                    Toast.makeText(this, "Se eliminó con éxito", Toast.LENGTH_SHORT).show();
                    limpiar();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void limpiar() {
        etNombre.setText("");
        etDescripcion.setText("");
        etPrecio.setText("");
        etCantidad.setText("");
        etStockMinimo.setText("");
        etNombre.setError(null);
        etDescripcion.setError(null);
        etPrecio.setError(null);
        etCantidad.setError(null);
        etStockMinimo.setError(null);
        seleccionado = null;
        etNombre.requestFocus();
    }
}