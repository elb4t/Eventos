package tk.elb4t.eventos;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by eloy on 3/3/17.
 */

public class EventoDetalles extends AppCompatActivity {
    @BindView(R.id.txtEvento)
    TextView txtEvento;
    @BindView(R.id.txtFecha)
    TextView txtFecha;
    @BindView(R.id.txtCiudad)
    TextView txtCiudad;
    @BindView(R.id.imgImagen)
    ImageView imgImagen;
    String evento;
    Query registro;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.evento_detalles);
        ButterKnife.bind(this);
        Bundle extras = getIntent().getExtras();
        evento = extras.getString("evento");
        if (evento == null) evento = "";
        registro = EventosAplicacion.getItemsReference().child(evento);
        registro.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                EventoItem currentItem = snapshot.getValue(EventoItem.class);
                txtEvento.setText(currentItem.getEvento());
                txtCiudad.setText(currentItem.getCiudad());
                txtFecha.setText(currentItem.getFecha());
                new DownloadImageTask(
                        (ImageView) imgImagen).execute(currentItem.getImagen());
            }

            @Override
            public void onCancelled(DatabaseError error) {
                EventosAplicacion.mostrarDialogo(
                        EventosAplicacion.getAppContext(), "Ha ocurrido un error al recuperar el registro.");
            }
        });
    }

    private class DownloadImageTask
            extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        public DownloadImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        protected Bitmap doInBackground(String... urls) {
            String urldisplay = urls[0];
            Bitmap mImagen = null;
            try {
                InputStream in = new java.net.URL(urldisplay).openStream();
                mImagen = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return mImagen;
        }

        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);
        }
    }
}
