package com.company;


import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import sun.security.provider.MD5;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/*
 *
 * Goal: practice Java programming, NIO file operations and using a library called JSON.simple
 * by writing a program to consolidate bookmarks from all my Profiles on Brave.

 * Key features/points: It reads all the JSON files. It makes a superset, by creating
 * bookmark folders named for the profile's folder on disk. It throws away any profile-name
 * level empty lists. Profile 11 is the "sacrifice" profile/folder. The superset ends
 * up in that Brave profile.

 * Backed up: back up this consolidated bookmarks HTML file. It's now been copied to
 * C:\Users\symed\AppData\Local\BraveSoftware\Brave-Browser\User Data\bookmarks_7_20_20.html and
 * C:\Users\symed\AppData\Local\BraveSoftware\Brave-Browser\User Data\Profile 11\bookmarks_7_20_20.html
 * and has been backed up to NAS.
 *
 * Example of where to find the bookmark data.
 * C:\Users\symed\AppData\Local\BraveSoftware\Brave-Browser\User Data\Profile 1\Bookmarks
 * is a JSON file altho it has no extension.
 *
 * C:\Users\symed\AppData\Local\BraveSoftware\Brave-Browser\User Data\Profile*\Bookmarks
 *
 */
public class Main {

    static final String sacrificeProfileName = "Profile 11"; // the bookmarks of profile 11 will be overwritten
    static final String braveAppDataPath = "\\AppData\\Local\\BraveSoftware\\Brave-Browser\\User Data\\";
    static final String bookmarkFileName = "Bookmarks";
    static final String getBookmarkFileNames = "regex:.*Profile [0-9]+\\\\Bookmarks";
    static final String getPreferencesFilesNames = "regex:.*Profile [0-9]+\\\\Preferences";

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.println();
        System.out.println("Exit Brave browser");
        System.out.println("Then press enter");
        in.readLine();

        String userHome = System.getProperty("user.home");
        String braveProfilePath = userHome + braveAppDataPath;
        Path pa = Paths.get(braveProfilePath);
        PathMatcher pmp = pa.getFileSystem().getPathMatcher(getPreferencesFilesNames);

        PathMatcher pm = pa.getFileSystem().getPathMatcher(getBookmarkFileNames);

        try (
                Stream<Path> profilePathsStream = Files.find(pa,
                        2,
                        ((p, a) -> {
                            return pm.matches(p) && (a.isRegularFile());
                        }));
                Stream<Path> profilePrefsStream = Files.find(pa,
                        2,
                        ((p, a) -> {
                            return pmp.matches(p) && (a.isRegularFile());
                        })))
        { // this is the try block
            JSONObject superset = null;
            List<Path> profilePaths = profilePathsStream.collect(Collectors.toList());
            List<Path> preferencesPaths = profilePrefsStream.collect(Collectors.toList());
            Iterator<Path> prefIterator = preferencesPaths.iterator();

            for (Path path : profilePaths) {
                System.out.println(path.toString());
                String profileName = "";
                if (prefIterator.hasNext()) {
                    try (FileReader frp = new FileReader(prefIterator.next().toAbsolutePath().toString())) {
                        try {
                            Object prefFileObj = new JSONParser().parse(frp);
                            // typecasting obj to JSONObject
                            JSONObject wholeThing = (JSONObject) prefFileObj;
                            profileName = ((JSONObject)wholeThing.get("profile")).get("name").toString();
                        } catch (ParseException e) {
                            System.out.println(e.getMessage());
                        }
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                    }
                }
                try (FileReader fr = new FileReader(path.toAbsolutePath().toString())) {
                    try {
                        Object obj = new JSONParser().parse(fr);

                        // typecasting obj to JSONObject
                        JSONObject wholeThing = (JSONObject) obj;
                        if (superset == null) {
                            superset = (JSONObject) ((JSONObject) wholeThing).clone();

                            superset.put("roots",
                                    ((JSONObject) ((JSONObject) superset.get("roots")).clone()));

                            ((JSONObject) ((JSONObject) superset.get("roots"))).put(
                                    "bookmark_bar",
                                    ((JSONObject) ((JSONObject) wholeThing.get("roots"))
                                            .get("bookmark_bar")).clone()
                            );
                            ((JSONObject) ((JSONObject) ((JSONObject) superset.get("roots"))).get(
                                    "bookmark_bar"))
                                    .put("children", new JSONArray());

                            ((JSONObject) ((JSONObject) superset.get("roots"))).put(
                                    "synced",
                                    ((JSONObject) ((JSONObject) wholeThing.get("roots"))
                                            .get("synced")).clone()
                            );
                            ((JSONObject) ((JSONObject) ((JSONObject) superset.get("roots"))).get(
                                    "synced"))
                                    .put("children", new JSONArray());

                            ((JSONObject) ((JSONObject) superset.get("roots"))).put(
                                    "other",
                                    ((JSONObject) ((JSONObject) wholeThing.get("roots"))
                                            .get("other")).clone()
                            );
                            ((JSONObject) ((JSONObject) ((JSONObject) superset.get("roots"))).get(
                                    "other"))
                                    .put("children", new JSONArray());
                        }

                        // profile name : path.getName( path.getNameCount()-2 )
                        if (profileName.equals("")) {
                            profileName = path.getName(path.getNameCount() - 2).toString();
                            profileName = profileName.replace(' ', '_');
                        }
                        if (profileName.equals(sacrificeProfileName.replace(' ', '_')))
                            continue;
                        if (profileName.contains("sacrifice")) {
                            continue;
                        }

                        JSONObject bookmark_bar = (JSONObject) ((JSONObject) ((JSONObject)
                                (wholeThing.get("roots"))).get("bookmark_bar")).clone();
                        StringBuilder sb = new StringBuilder();
                        sb.append(profileName);
                        sb.append("_");
                        sb.append(bookmark_bar.get("name"));
                        bookmark_bar.put("name", sb.toString());
                        ((JSONArray) ((JSONObject) ((JSONObject) superset.get("roots")).get("bookmark_bar"))
                                .get("children")).add(bookmark_bar);

                        sb.delete(profileName.length() + 1, sb.length());
                        JSONObject other = (JSONObject) ((JSONObject) ((JSONObject) (wholeThing.get("roots"))).get("other")
                        ).clone();
                        sb.append(other.get("name"));
                        other.put("name", sb.toString());
                        ((JSONArray) ((JSONObject) ((JSONObject) superset.get("roots")).get("other"))
                                .get("children")).add(other);

                        JSONObject synced = (JSONObject) ((JSONObject) ((JSONObject) (wholeThing.get("roots")))
                                .get("synced")).clone();
                        sb.delete(profileName.length() + 1, sb.length());
                        sb.append(synced.get("name"));
                        synced.put("name", sb.toString());
                        ((JSONArray) ((JSONObject) ((JSONObject) superset.get("roots")).get("synced"))
                                .get("children")).add(synced);


                    } catch (ParseException pe) {
                        System.out.println(pe.getMessage());
                    }
                }
            }
//            static final String sacrificeProfileName = "Profile 11"; // the bookmarks of profile 11 will be overwritten
//            static final String braveAppDataPath = "\\AppData\\Local\\BraveSoftware\\Brave-Browser\\User Data\\";
//            Iterator it = ((JSONArray)((JSONObject)((JSONObject)superset.get("roots")).get("bookmark_bar"))
//                    .get("children")).iterator();
            List<JSONObject> toDelete = new ArrayList<>();
            JSONArray bja = (JSONArray) ((JSONObject) ((JSONObject) superset.get("roots")).get("bookmark_bar"))
                    .get("children");
            for (int i = 0; i < bja.size(); i++) {
                JSONObject jo = (JSONObject) bja.get(i);
                JSONArray ja = (JSONArray) jo.get("children");
                if (ja.size() == 0) {
                    toDelete.add(jo);
                }
            }
            for (JSONObject joo : toDelete) {
                bja.remove(joo);
            }

            toDelete = new ArrayList<>();
            bja = (JSONArray) ((JSONObject) ((JSONObject) superset.get("roots")).get("other"))
                    .get("children");
            for (int i = 0; i < bja.size(); i++) {
                JSONObject jo = (JSONObject) bja.get(i);
                JSONArray ja = (JSONArray) jo.get("children");
                if (ja.size() == 0) {
                    toDelete.add(jo);
                }
            }
            for (JSONObject joo : toDelete) {
                bja.remove(joo);
            }
            toDelete = new ArrayList<>();
            bja = (JSONArray) ((JSONObject) ((JSONObject) superset.get("roots")).get("synced"))
                    .get("children");
            for (int i = 0; i < bja.size(); i++) {
                JSONObject jo = (JSONObject) bja.get(i);
                JSONArray ja = (JSONArray) jo.get("children");
                if (ja.size() == 0) {
                    toDelete.add(jo);
                }
            }
            for (JSONObject joo : toDelete) {
                bja.remove(joo);
            }

            // writing JSON to file:"JSONExample.json" in cwd
            String bookmarkSuperName =
                    userHome + braveAppDataPath + sacrificeProfileName + "\\" + bookmarkFileName;
            System.out.println("Output file name: " + bookmarkSuperName);

            PrintWriter pw = new PrintWriter(bookmarkSuperName);
            pw.write(superset.toJSONString());

            pw.flush();
            pw.close();
            // now write the new JSON file

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            System.out.println("Press enter to finish.");
            in.readLine();
        }

    }

    public static long getCRC32Checksum(byte[] bytes) {
        Checksum crc32 = new CRC32();
//        SignatureDSA.SHA256
        MD5 md5 = new MD5();
        crc32.update(bytes, 0, bytes.length);
        return crc32.getValue();
    }

    static public String md5CheckSum(byte[] bytes)
            throws IOException {

        HashFunction md5 = Hashing.md5();
        Hasher hash = md5.newHasher();
        hash.putBytes(bytes);
        return hash.hash().toString();
//        String myChecksum = hash.toString()
//                .toUpperCase();

    }
}
