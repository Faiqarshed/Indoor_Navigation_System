package com.google.ar.core.examples.java.cloudanchor;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.google.firebase.database.*;
import java.util.*;


class GraphBuilder1 {
    private DatabaseReference anchorsRef;
    private DatabaseReference anchorConnectionsRef;
    private DatabaseReference detectedImagesRef;

    private Map<String, List<String>> anchorConnections = new HashMap<>();
    private Map<String, AnchorData> anchors = new HashMap<>();
    private Map<String, String> detectedImages = new HashMap<>();
    private Map<String, LandmarkNode> graph = new HashMap<>();
    private Map<String, String> landmarkToAnchorMap = new HashMap<>(); // Landmark ID → Anchor ID
    private Context context;
    private String current;
    private String destination;

    public GraphBuilder1(Context context) {
        this.context = context;
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        detectedImagesRef = database.getReference("Landmarks");
        anchorConnectionsRef = database.getReference("anchorConnections");
        anchorsRef = database.getReference("anchors");
    }
    public void buildGraph() {
        Log.e("GraphBuilder", "BuildGraph Launched");
        FirebaseDataRetrieval();
    }

    public void startandend(String curr, String dest){
        current = curr;
        destination = dest;
    }

    private void FirebaseDataRetrieval() {
        FirebaseDatabase database = FirebaseDatabase.getInstance();

        // Reference Firebase database nodes
        anchorsRef = database.getReference("anchors");
        anchorConnectionsRef = database.getReference("anchorConnections");
        detectedImagesRef = database.getReference("detected_images");

        // Implement a clear sequential flow with callbacks
        retrieveAnchorData(() -> {
            retrieveDetectedImages(() -> {
                retrieveAnchorConnections(() -> {
                    // Process after ALL data is retrieved
                    Log.e("Debug", "All data retrieved. Now processing connections...");
                    processLandmarkConnections();
                });
            });
        });
    }

    private void retrieveAnchorData(Runnable callback) {
        anchorsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Your existing code...
                Log.e("Cunt1", "Anchor Data Snapshot Exists: " + dataSnapshot.exists());

                anchors.clear();
                for (DataSnapshot anchorSnapshot : dataSnapshot.getChildren()) {
                    String anchorId = anchorSnapshot.getKey();
                    String cloudAnchorId = anchorSnapshot.child("cloudAnchorid").getValue(String.class);
                    Integer roomCode = anchorSnapshot.child("roomCode").getValue(Integer.class);
                    Double x = anchorSnapshot.child("x").getValue(Double.class);
                    Double y = anchorSnapshot.child("y").getValue(Double.class);
                    Double z = anchorSnapshot.child("z").getValue(Double.class);

                    Log.e("Cunt1", "Fetched Anchor: " + anchorId + ", CloudAnchorID: " + cloudAnchorId);

                    if (cloudAnchorId != null && roomCode != null && x != null && y != null && z != null) {
                        anchors.put(anchorId, new AnchorData(anchorId, cloudAnchorId, roomCode, x, y, z));
                    }
                }

                Log.e("Debug", "Total Anchors Retrieved: " + anchors.size());
                Log.e("Debug", "Final Anchor Map: " + anchors);

                callback.run(); // Call the next step when done
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("Debug", "Error fetching anchor data: " + databaseError.getMessage());
            }
        });
    }


    private void retrieveAnchorConnections(Runnable callback) {
        anchorConnectionsRef.addListenerForSingleValueEvent(new ValueEventListener() { // Changed to SingleValueEvent
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Your existing code...
                anchorConnections.clear();
                graph.clear(); // Clear graph before populating it
                Log.e("Cunt3", "Anchor Connections Retrieved");

                for (DataSnapshot anchorSnapshot : dataSnapshot.getChildren()) {
                    String anchorId = anchorSnapshot.getKey();
                    List<String> connections = new ArrayList<>();

                    for (DataSnapshot connectedAnchor : anchorSnapshot.getChildren()) {
                        connections.add(connectedAnchor.getKey());
                    }

                    anchorConnections.put(anchorId, connections);
                    Log.e("Cunt3", "Connections for " + anchorId + ": " + connections);
                }

                Log.e("Cunt3", "Anchor Connections Size: " + anchorConnections.size());

                // 🌟 Populate graph here before calling processLandmarkConnections()
                if (graph == null) {
                    graph = new HashMap<>();
                }

                initializeGraph();
                Log.e("Debug", "Graph initialized with " + graph.size() + " nodes.");

                callback.run(); // Call the next step when done
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("Debug", "Error fetching anchor connections: " + databaseError.getMessage());
            }
        });
    }

    private void initializeGraph() {
        graph.clear();

        // Initialize graph with landmark IDs from landmarkToAnchorMap
        for (String landmarkId : landmarkToAnchorMap.keySet()) {
            graph.put(landmarkId, new LandmarkNode(landmarkId, new ArrayList<>(), new ArrayList<>()));
        }

        Log.e("Debug", "Graph initialized with landmarks: " + graph.keySet());
    }





    private void retrieveDetectedImages(Runnable callback) {
        detectedImagesRef.addListenerForSingleValueEvent(new ValueEventListener() { // Changed to SingleValueEvent
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Your existing code for retrieving images...
                detectedImages.clear();
                Log.e("Cunt2", "Detected Images Retrieved");

                for (DataSnapshot imageSnapshot : dataSnapshot.getChildren()) {
                    String imageId = imageSnapshot.child("imageId").getValue(String.class);
                    String cloudAnchorId = imageSnapshot.child("cloudAnchorId").getValue(String.class);

                    if (imageId != null && cloudAnchorId != null) {
                        detectedImages.put(imageId, cloudAnchorId);
                        Log.e("Cunt2", "Detected Image Added: " + imageId + " -> " + cloudAnchorId);

                        Log.e("Cunt2", "Anchors map size: " + anchors.size());
                        for (AnchorData anchor : anchors.values()) {
                            if (anchor.cloudAnchorId.equals(cloudAnchorId)) {
                                updateDetectedImageInFirebase(imageId, anchor.anchorId);
                            }
                        }
                    }
                }


                // Once images are retrieved, fetch the landmark-to-anchor mapping
                fetchLandmarkToAnchorMap(callback); // Pass the callback through
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("Debug", "Error fetching detected images: " + databaseError.getMessage());
            }
        });
    }


    private void fetchLandmarkToAnchorMap(Runnable callback) {
        // Use VALUE (not SingleValue) to ensure we get ALL the data
        detectedImagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                landmarkToAnchorMap.clear();
                Log.e("Debug", "Fetching Updated Landmark-to-Anchor Map...");

                // Count how many valid mappings we found
                int validMappingsCount = 0;

                for (DataSnapshot imageSnapshot : dataSnapshot.getChildren()) {
                    String landmarkId = imageSnapshot.child("imageId").getValue(String.class);
                    String anchorId = imageSnapshot.child("matchedAnchorId").getValue(String.class);

                    if (landmarkId != null && anchorId != null) {
                        landmarkToAnchorMap.put(landmarkId, anchorId);
                        validMappingsCount++;
                        Log.e("Debug", "Mapped Landmark " + landmarkId + " → Anchor " + anchorId);
                    }
                }

                Log.e("Debug", "Landmark-to-Anchor Map successfully fetched with " +
                        validMappingsCount + " valid mappings: " + landmarkToAnchorMap.toString());

                // Only continue if we actually have some mappings
                if (validMappingsCount > 0) {
                    callback.run();
                } else {
                    Log.e("Debug", "WARNING: No valid landmark-to-anchor mappings found!");
                    // You could retry here or handle the empty case
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e("Debug", "Error fetching landmark-to-anchor map: " + databaseError.getMessage());
            }
        });
    }




    private void updateDetectedImageInFirebase(String imageId, String anchorId) {
        DatabaseReference detectedImageRef = detectedImagesRef.child(imageId);
        detectedImageRef.child("matchedAnchorId").setValue(anchorId)
                .addOnSuccessListener(aVoid -> {
                    Log.e("Debug", "Updated detected image: " + imageId + " with anchorId: " + anchorId);
                    // Also update the local map immediately
                    landmarkToAnchorMap.put(imageId, anchorId);
                })
                .addOnFailureListener(e -> Log.e("Debug", "Error updating detected image: " + e.getMessage()));
    }

    private void processLandmarkConnections() {
        Log.e("Cunt3", "Graph object: " + graph);
        Log.e("Debug", "landmarkToAnchorMap: " + landmarkToAnchorMap);

        // CRITICAL: We need to check what's actually in these data structures
        Log.e("Debug", "Graph keys: " + graph.keySet());
        Log.e("Debug", "landmarkToAnchorMap keys: " + landmarkToAnchorMap.keySet());

        // First check: Maybe we're looking up incorrectly
        for (String landmark1 : landmarkToAnchorMap.keySet()) {
            Log.e("Debug", "Looking for landmark1: " + landmark1 + " in graph: " + (graph.containsKey(landmark1) ? "FOUND" : "NOT FOUND"));
        }

        // Create an inverse map for lookups in the other direction
        Map<String, String> anchorToLandmarkMap = new HashMap<>();
        for (Map.Entry<String, String> entry : landmarkToAnchorMap.entrySet()) {
            anchorToLandmarkMap.put(entry.getValue(), entry.getKey());
        }

        // Modified approach using the keys we know exist
        for (String landmark1Key : landmarkToAnchorMap.keySet()) {
            for (String landmark2Key : landmarkToAnchorMap.keySet()) {
                if (!landmark1Key.equals(landmark2Key)) {
                    String anchor1 = landmarkToAnchorMap.get(landmark1Key);
                    String anchor2 = landmarkToAnchorMap.get(landmark2Key);

                    Log.e("Debug", "Processing: " + landmark1Key + " -> " + anchor1 + " and " + landmark2Key + " -> " + anchor2);

                    if (anchor1 != null && anchor2 != null) {
                        // First, make sure the landmark nodes exist in the graph
                        if (!graph.containsKey(landmark1Key)) {
                            Log.e("Debug", "Creating missing node for: " + landmark1Key);
                            graph.put(landmark1Key, new LandmarkNode(landmark1Key, new ArrayList<>(), new ArrayList<>()));
                        }

                        if (!graph.containsKey(landmark2Key)) {
                            Log.e("Debug", "Creating missing node for: " + landmark2Key);
                            graph.put(landmark2Key, new LandmarkNode(landmark2Key, new ArrayList<>(), new ArrayList<>()));
                        }

                        List<String> shortestPath = findShortestPath(anchor1, anchor2);
                        if (!shortestPath.isEmpty()) {
                            // Rest of your logic stays the same...
                            if (!graph.get(landmark1Key).connectedLandmarks.contains(landmark2Key)) {
                                graph.get(landmark1Key).connectedLandmarks.add(landmark2Key);
                            }

                            graph.get(landmark1Key).path.clear();
                            graph.get(landmark1Key).path.addAll(shortestPath);

                            if (!graph.get(landmark2Key).connectedLandmarks.contains(landmark1Key)) {
                                graph.get(landmark2Key).connectedLandmarks.add(landmark1Key);
                            }

                            List<String> reversedPath = new ArrayList<>(shortestPath);
                            Collections.reverse(reversedPath);
                            graph.get(landmark2Key).path.clear();
                            graph.get(landmark2Key).path.addAll(reversedPath);
                        }
                    }
                }
            }
        }

        // Store the graph in Firebase after fixing paths
        storeGraphInFirebase();
        Log.e("Cuntfine","Trying to launch the function LaunchGraphActivity");
        launchGraphActivity();  // 'this' is the Activity context

    }

    public void launchGraphActivity() {
        Intent intent = new Intent(context, GraphActivity.class);
        intent.putExtra("CURRENT_LOCATION", current);
        intent.putExtra("DESTINATION_LOCATION", destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Add this flag
        context.startActivity(intent);
    }




    private List<String> findShortestPath(String start, String end) {
        Log.e("Cunt4", "Find Shortest Path Called");
        if (!anchorConnections.containsKey(start) || !anchorConnections.containsKey(end)) {
            return new ArrayList<>();
        }

        Queue<List<String>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        queue.add(Collections.singletonList(start));
        visited.add(start);

        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String lastNode = path.get(path.size() - 1);

            if (lastNode.equals(end)) {
                return path; // Stop at first occurrence of the destination
            }

            for (String neighbor : anchorConnections.getOrDefault(lastNode, new ArrayList<>())) {
                if (!visited.contains(neighbor)) {
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.add(newPath);
                    visited.add(neighbor);
                }
            }
        }
        return new ArrayList<>();
    }

    public Map<String, LandmarkNode> getGraph() {
        return graph;
    }

    private void storeGraphInFirebase() {
        Log.e("Cunt4", "Store Graph In Firebase Called");

        DatabaseReference graphRef = FirebaseDatabase.getInstance().getReference("navigationGraph");

        Map<String, Object> graphData = new HashMap<>();

        for (Map.Entry<String, LandmarkNode> entry : graph.entrySet()) {
            String landmarkId = entry.getKey();
            LandmarkNode node = entry.getValue();

            Map<String, Object> nodeData = new HashMap<>();
            nodeData.put("connectedLandmarks", node.connectedLandmarks);
            nodeData.put("path", node.path);

            graphData.put(landmarkId, nodeData);
        }

        graphRef.setValue(graphData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("GraphBuilder", "Graph stored successfully in Firebase.");
            } else {
                Log.e("GraphBuilder", "Failed to store graph in Firebase.", task.getException());
            }
        });
    }



    public static class AnchorData {
        String anchorId;
        String cloudAnchorId;
        int roomCode;
        double x, y, z;

        public AnchorData(String anchorId, String cloudAnchorId, int roomCode, double x, double y, double z) {
            this.anchorId = anchorId;
            this.cloudAnchorId = cloudAnchorId;
            this.roomCode = roomCode;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String toString() {
            return "AnchorData{" +
                    "anchorId='" + anchorId + '\'' +
                    ", cloudAnchorId='" + cloudAnchorId + '\'' +
                    ", roomCode=" + roomCode +
                    ", x=" + x +
                    ", y=" + y +
                    ", z=" + z +
                    '}';
        }
    }

    public static class LandmarkNode {
        public String landmarkId;
        public List<String> connectedLandmarks;
        public List<String> path;

        public LandmarkNode(String landmarkId, List<String> connections, List<String> paths) {
            this.landmarkId = landmarkId;
            this.connectedLandmarks = connections;
            this.path = paths;
        }


    }


}