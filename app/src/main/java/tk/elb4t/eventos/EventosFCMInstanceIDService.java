package tk.elb4t.eventos;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import static tk.elb4t.eventos.EventosAplicacion.guardarIdRegistro;

/**
 * Created by eloy on 2/3/17.
 */

public class EventosFCMInstanceIDService
        extends FirebaseInstanceIdService {
    @Override
    public void onTokenRefresh() {
        String idPush;
        idPush = FirebaseInstanceId.getInstance().getToken();
        guardarIdRegistro(getApplicationContext(), idPush);
    }
}