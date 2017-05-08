package ioana.simple;

import android.annotation.TargetApi;
import android.os.Build;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.protobuf.ByteString;

import java.util.Date;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;

class Singleton {
    private static Singleton mInstance = null;
    private ListView proofNameList;
    private List<ProofProtos.SignedLocnProof> proofList;

    private Singleton() {
        proofList = new LinkedList<>();
    }

    static Singleton getInstance(){
        if(mInstance == null) {
            mInstance = new Singleton();
        }
        return mInstance;
    }

    List<ProofProtos.SignedLocnProof> getProofList() {
        return proofList;
    }

    public void setProofList(List<ProofProtos.SignedLocnProof> proofList) {
        this.proofList = proofList;
    }

    public ListView getProofNameList() {
        return proofNameList;
    }

    void setProofNameList(ListView proofNameList) {
        this.proofNameList = proofNameList;
    }

    void addToList(ProofProtos.SignedLocnProof locnProof) {
        proofList.add(locnProof);
        Date date = new Date(locnProof.getLocnproof().getTime());
        ArrayAdapter adapter = (ArrayAdapter) proofNameList.getAdapter();
        ByteString apid = locnProof.getLocnproof().getApid();
        adapter.add(apid.toString() +" at "
                + date.toString());
        adapter.notifyDataSetChanged();
    }
}
