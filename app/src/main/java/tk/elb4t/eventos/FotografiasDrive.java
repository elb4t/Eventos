package tk.elb4t.eventos;

import android.*;
import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

/**
 * Created by eloy on 4/3/17.
 */

public class FotografiasDrive extends AppCompatActivity {
    public TextView mDisplay;
    String evento;

    static Drive servicio = null;
    static GoogleAccountCredential credencial = null;
    static String nombreCuenta = null;
    static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    static final String DISPLAY_MESSAGE_ACTION =
            "tk.elb4t.eventos.DISPLAY_MESSAGE";
    private static Handler manejador = new Handler();
    private static Handler carga = new Handler();
    private static ProgressDialog dialogo;
    static final int SOLICITUD_SELECCION_CUENTA = 1;
    static final int SOLICITUD_AUTORIZACION = 2;
    static final int SOLICITUD_SELECCIONAR_FOTOGRAFIA = 3;
    static final int SOLICITUD_HACER_FOTOGRAFIA = 4;
    private static Uri uriFichero;

    private String idCarpeta = "";
    private String idCarpetaEvento = "";
    private Boolean noAutoriza = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fotografias_drive);
        registerReceiver(mHandleMessageReceiver, new IntentFilter(DISPLAY_MESSAGE_ACTION));
        mDisplay = (TextView) findViewById(R.id.display);
        Bundle extras = getIntent().getExtras();
        evento = extras.getString("evento");
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActivityCompat.requestPermissions(FotografiasDrive.this,
                new String[]{android.Manifest.permission.GET_ACCOUNTS}, 3);

        credencial = GoogleAccountCredential.usingOAuth2(this, Arrays.asList(DriveScopes.DRIVE));
        SharedPreferences prefs = getSharedPreferences("Preferencias", Context.MODE_PRIVATE);
        nombreCuenta = prefs.getString("nombreCuenta", null);
        noAutoriza = prefs.getBoolean("noAutoriza", false);
        idCarpeta = prefs.getString("idCarpeta", null);
        idCarpetaEvento = prefs.getString("idCarpeta_" + evento, null);
        if (!noAutoriza) {
            if (nombreCuenta == null) {
                PedirCredenciales();
            } else {
                credencial.setSelectedAccountName(nombreCuenta);
                servicio = obtenerServicioDrive(credencial);
                if (idCarpetaEvento == null) {
                    crearCarpetaEnDrive(evento, idCarpeta);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_drive, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        View vista = (View) findViewById(android.R.id.content);
        int id = item.getItemId();
        switch (id) {
            case R.id.action_camara:
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    if (!noAutoriza) {
                        hacerFoto(vista);
                    }
                } else {
                    ActivityCompat.requestPermissions(FotografiasDrive.this,
                            new String[]{android.Manifest.permission.CAMERA}, 2);
                }

                break;
            case R.id.action_galeria:
                if (!noAutoriza) {
                    seleccionarFoto(vista);
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    static void mostrarMensaje(final Context context, final String mensaje) {
        manejador.post(new Runnable() {
            public void run() {
                Toast.makeText(context, mensaje, Toast.LENGTH_SHORT).show();
            }
        });
    }

    static void mostrarCarga(final Context context, final String mensaje) {
        carga.post(new Runnable() {
            public void run() {
                dialogo = new ProgressDialog(context);
                dialogo.setMessage(mensaje);
                dialogo.show();
            }
        });
    }

    static void ocultarCarga(final Context context) {
        carga.post(new Runnable() {
            public void run() {
                dialogo.dismiss();
            }
        });
    }

    private void PedirCredenciales() {
        if (nombreCuenta == null) {
            startActivityForResult(credencial.newChooseAccountIntent(), SOLICITUD_SELECCION_CUENTA);
        }
    }

    @Override
    protected void onActivityResult(final int requestCode,
                                    final int resultCode, final Intent data) {
        switch (requestCode) {
            case SOLICITUD_SELECCION_CUENTA:
                if (resultCode == RESULT_OK && data != null
                        && data.getExtras() != null) {
                    nombreCuenta = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (nombreCuenta != null) {
                        credencial.setSelectedAccountName(nombreCuenta);
                        servicio = obtenerServicioDrive(credencial);
                        SharedPreferences prefs = getSharedPreferences(
                                "Preferencias", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("nombreCuenta", nombreCuenta);
                        editor.commit();
                        crearCarpetaEnDrive(evento, idCarpeta);
                    }
                }
                break;
            case SOLICITUD_HACER_FOTOGRAFIA:
                if (resultCode == Activity.RESULT_OK) {
                    guardarFicheroEnDrive(this.findViewById(android.R.id.content));
                }
                break;
            case SOLICITUD_SELECCIONAR_FOTOGRAFIA:
                if (resultCode == Activity.RESULT_OK) {
                    Uri ficheroSeleccionado = data.getData();
                    String[] proyeccion = {MediaStore.Images.Media.DATA};
                    Cursor cursor = managedQuery(ficheroSeleccionado, proyeccion, null, null,
                            null);
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    cursor.moveToFirst();
                    uriFichero = Uri.fromFile(new java.io.File(cursor.getString(column_index)));
                    guardarFicheroEnDrive(this.findViewById(android.R.id.content));
                }
                break;
            case SOLICITUD_AUTORIZACION:
                if (resultCode == Activity.RESULT_OK) {
                    crearCarpetaEnDrive(evento, idCarpeta);
                } else {
                    noAutoriza = true;
                    SharedPreferences prefs = getSharedPreferences("Preferencias",
                            Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean("noAutoriza", true);
                    editor.commit();
                    mostrarMensaje(this, "El usuario no autoriza usar Google Drive");
                }
                break;
        }
    }

    private Drive obtenerServicioDrive(GoogleAccountCredential credencial) {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(),
                new GsonFactory(), credencial).build();
    }

    private void crearCarpetaEnDrive(final String nombreCarpeta,
                                     final String carpetaPadre) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String idCarpetaPadre = carpetaPadre;
                    mostrarCarga(FotografiasDrive.this, "Creando carpeta..."); //Crear carpeta EventosDrive
                    if (idCarpeta == null) {
                        File metadataFichero = new File();
                        metadataFichero.setName("EventosDrive");
                        metadataFichero.setMimeType("application/vnd.google-apps.folder");
                        File fichero = servicio.files().create(metadataFichero)
                                .setFields("id").execute();
                        if (fichero.getId() != null) {
                            SharedPreferences prefs = getSharedPreferences("Preferencias",
                                    Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString("idCarpeta", fichero.getId());
                            editor.commit();
                            idCarpetaPadre = fichero.getId();
                        }
                    }
                    File metadataFichero = new File();
                    metadataFichero.setName(nombreCarpeta);
                    metadataFichero.setMimeType("application/vnd.google-apps.folder");
                    if (!idCarpetaPadre.equals("")) {
                        metadataFichero.setParents(Collections.singletonList(idCarpetaPadre));
                    }
                    File fichero = servicio.files().create(metadataFichero).setFields("id").execute();
                    if (fichero.getId() != null) {
                        SharedPreferences prefs = getSharedPreferences("Preferencias", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("idCarpeta_" + evento, fichero.getId());
                        editor.commit();
                        idCarpetaEvento = fichero.getId();
                        mostrarMensaje(FotografiasDrive.this, "¡Carpeta creada!");
                    }
                    ocultarCarga(FotografiasDrive.this);
                } catch (UserRecoverableAuthIOException e) {
                    ocultarCarga(FotografiasDrive.this);
                    startActivityForResult(e.getIntent(), SOLICITUD_AUTORIZACION);
                } catch (IOException e) {
                    mostrarMensaje(FotografiasDrive.this, "Error;" + e.getMessage());
                    Log.e("--------------", "---------" + e.getMessage());
                    ocultarCarga(FotografiasDrive.this);
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

    public void hacerFoto(View v) {
        if (nombreCuenta == null) {
            mostrarMensaje(this, "Debes seleccionar una cuenta de Google Drive");
        } else {
            String mediaStorageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath();
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(new Date());
            uriFichero = Uri.fromFile(new java.io.File(mediaStorageDir + java.io.File.separator + "IMG_" + timeStamp + ".jpg"));
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uriFichero);
            startActivityForResult(cameraIntent, SOLICITUD_HACER_FOTOGRAFIA);
        }
    }

    public void seleccionarFoto(View v) {
        if (nombreCuenta == null) {
            mostrarMensaje(this, "Debes seleccionar una cuenta de Google Drive");
        } else {
            Intent seleccionFotografiaIntent = new Intent();
            seleccionFotografiaIntent.setType("image/*");
            seleccionFotografiaIntent.setAction(Intent.ACTION_PICK);
            startActivityForResult(Intent.createChooser(seleccionFotografiaIntent, "Seleccionar fotografía"), SOLICITUD_SELECCIONAR_FOTOGRAFIA);
        }
    }

    private void guardarFicheroEnDrive(final View view) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mostrarCarga(FotografiasDrive.this, "Subiendo imagen...");
                    java.io.File ficheroJava = new java.io.File(uriFichero.getPath());
                    FileContent contenido = new FileContent("image/jpeg", ficheroJava);
                    File ficheroDrive = new File();
                    ficheroDrive.setName(ficheroJava.getName());
                    ficheroDrive.setMimeType("image/jpeg");
                    ficheroDrive.setParents(Collections.singletonList(idCarpetaEvento));
                    File ficheroSubido = servicio.files().create(ficheroDrive,
                            contenido).setFields("id").execute();
                    mostrarMensaje(FotografiasDrive.this, "¡Foto subida!");
                    if (ficheroSubido.getId() != null) {
                    }
                    ocultarCarga(FotografiasDrive.this);
                } catch (UserRecoverableAuthIOException e) {
                    ocultarCarga(FotografiasDrive.this);
                    startActivityForResult(e.getIntent(), SOLICITUD_AUTORIZACION);
                } catch (IOException e) {
                    mostrarMensaje(FotografiasDrive.this, "Error;" + e.getMessage());
                    ocultarCarga(FotografiasDrive.this);
                    e.printStackTrace();
                }
            }
        });
        t.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 2: {
                if (!(grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(FotografiasDrive.this,
                            "Permiso denegado para acceder a la camara", Toast.LENGTH_SHORT).show();
                }
                return;
            }
            case 3:
                if (!(grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(FotografiasDrive.this,
                            "Permiso denegado para acceder a las cuentas", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
}
