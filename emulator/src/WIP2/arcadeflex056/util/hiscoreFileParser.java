/**
 * java specific code for reading hiscore.dat for arcadeflex
 */
package WIP2.arcadeflex056.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import WIP2.mame056.hiscore.mem_range;
import static WIP2.mame056.hiscore.state;
import static WIP2.arcadeflex036.osdepend.logerror;

public class hiscoreFileParser {

    private static File file = null;

    public static int loadHiscoreFile(String fileName) {
        file = new File(fileName);
        if (file.exists()) {
            return 1;
        } else {
            return 0;
        }
    }

    public static void read(String gamename) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String line;
            boolean gamefound = false;
            boolean linefound = false;
            while ((line = reader.readLine()) != null) {
                if (!isComment(line)) {
                    line = cleanLine(line);//clear line with comments after the gamename or data lines
                    if (isGame(line)) {
                        String game = cleanGame(line);
                        if (game.equals(gamename)) {
                            gamefound = true;
                            logerror("hs config found!\n");
                        } else {
                            linefound = false;
                        }
                    } else if (isDataLine(line)) {
                        if (gamefound) {
                            //when we find the first dataline of game then gamefound=false so the next time it found a gameline that means that 
                            //game's datalines has ended
                            linefound = true;
                            gamefound = false;
                        }
                    }
                    if (!gamefound && linefound) {
                        String[] values = line.split(":");

                        if (!values[0].isEmpty()) {
                            mem_range mem_range = new mem_range();
                            mem_range.cpu = Integer.parseInt(values[0], 16);
                            mem_range.addr = Integer.parseInt(values[1], 16);
                            mem_range.num_bytes = Integer.parseInt(values[2], 16);
                            mem_range.start_value = Integer.parseInt(values[3], 16);
                            mem_range.end_value = Integer.parseInt(values[4], 16);
                            mem_range.next = null;
                            {
                                mem_range last = state.mem_range;
                                while (last != null && last.next != null) {
                                    last = last.next;
                                }
                                if (last == null) {
                                    state.mem_range = mem_range;
                                } else {
                                    last.next = mem_range;
                                }
                            }
                        }

                    }
                }

            }
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private static boolean isComment(String line) {
        return line.trim().startsWith(";");
    }

    private static String cleanLine(String line) {
        return line.trim().replaceFirst(";.*$", "").trim();
    }

    private static boolean isGame(String line) {
        return line.matches("^[^:]*:$");
    }

    private static String cleanGame(String line) {
        return line.substring(0, line.length() - 1).replaceAll(",", "_");
    }

    private static boolean isDataLine(String line) {
        return (line.matches("^[0-9a-fA-F][0-9a-fA-F]?:.*")) || (line.matches("^@:.*"));
    }
}
