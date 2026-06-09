package fr.neiyo.resonance;

import fr.neiyo.resonance.api.ResonanceProvider;
import fr.neiyo.resonance.core.ResonanceManager;

public class Resonance {

    public static void initialize() {
        ResonanceProvider.register(new ResonanceManager());
    }

}