package guessmarket.engine.persistence;

import guessmarket.engine.model.MarketState;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Writes a whole engine state to disk and reads it back with Java serialisation, which is why
 * everything reachable from {@link MarketState} is {@code Serializable}.
 * <p>
 * The caller gives a path with no extension and this class appends its own, so a saved state can
 * never be mistaken for a Guess Market XML file.
 */
public final class StateStore {

    /** Extension appended to every saved state. */
    public static final String STATE_EXTENSION = ".gmstate";

    /** @return the full path actually written. */
    public String save(String pathWithoutExtension, MarketState state) throws IOException {
        File file = resolve(pathWithoutExtension);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            throw new IOException("the folder '" + parent.getPath() + "' does not exist");
        }
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file))) {
            out.writeObject(state);
        }
        return file.getAbsolutePath();
    }

    public MarketState load(String pathWithoutExtension) throws IOException, ClassNotFoundException {
        File file = resolve(pathWithoutExtension);
        if (!file.exists()) {
            throw new IOException("there is no saved state at '" + file.getPath() + "'");
        }
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            Object restored = in.readObject();
            if (!(restored instanceof MarketState state)) {
                throw new IOException("the file '" + file.getPath() + "' does not hold a Guess Market state");
            }
            return state;
        }
    }

    public File resolve(String pathWithoutExtension) {
        String path = pathWithoutExtension == null ? "" : pathWithoutExtension.trim();
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1).trim();
        }
        if (path.toLowerCase().endsWith(STATE_EXTENSION)) {
            return new File(path);
        }
        return new File(path + STATE_EXTENSION);
    }
}
