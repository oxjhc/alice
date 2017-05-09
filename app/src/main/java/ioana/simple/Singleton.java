package ioana.simple;

import android.annotation.TargetApi;
import android.os.Build;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.google.protobuf.ByteString;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

class Singleton {
    private static Singleton mInstance = null;
    private ListView proofNameList;
    private List<ProofProtos.SignedLocnProof> proofList;
    private HashMap<Integer, String> nameMap;

    private Singleton() {
        proofList = new LinkedList<>();
        nameMap = new HashMap<>();
    }

    static Singleton getInstance(){
        if(mInstance == null) {
            mInstance = new Singleton();
        }
        return mInstance;
    }

    public void clearAll() {
        proofList.clear();
        nameMap.clear();
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) proofNameList.getAdapter();
        adapter.clear();
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

    public HashMap<Integer, String> getNameMap() {
        return nameMap;
    }

    void addToList(ProofProtos.SignedLocnProof locnProof, String name) {
        proofList.add(locnProof);
        Date date = new Date(1000 * locnProof.getLocnproof().getTime());
        ArrayAdapter adapter = (ArrayAdapter) proofNameList.getAdapter();
        adapter.add(name +" at "+ new SimpleDateFormat("dd/MM/yyyy HH:mm").format(date));
        nameMap.put(locnProof.hashCode(), name);
        adapter.notifyDataSetChanged();
    }
}
