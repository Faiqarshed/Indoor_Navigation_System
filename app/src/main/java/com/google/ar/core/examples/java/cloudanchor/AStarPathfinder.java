package com.google.ar.core.examples.java.cloudanchor;
import android.util.Log;

import java.util.*;


public class AStarPathfinder {
    static class Node implements Comparable<Node> {
        String id;
        float gCost, fCost; // g = actual cost, f = g + heuristic
        Node parent;

        Node(String id, float gCost, float fCost, Node parent) {
            this.id = id;
            this.gCost = gCost;
            this.fCost = fCost;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node other) {
            return Float.compare(this.fCost, other.fCost);
        }
    }

    public static List<String> findShortestPath(String start, String goal,
                                                Map<String, float[]> landmarkPositions,
                                                Map<String, List<String>> connections)
    {

        if (!landmarkPositions.containsKey(start) || landmarkPositions.get(start) == null) {
            Log.e("A*Error1", "🚨 Start landmark position is NULL! Key: " + start);
        }
        if (!landmarkPositions.containsKey(goal) || landmarkPositions.get(goal) == null) {
            Log.e("A*Error1", "🚨 End landmark position is NULL! Key: " + goal);
        }

        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Map<String, Float> gScores = new HashMap<>();
        Map<String, Node> allNodes = new HashMap<>();

        // Initialize start node
        Node startNode = new Node(start, 0, heuristic(landmarkPositions.get(start), landmarkPositions.get(goal)), null);
        openSet.add(startNode);
        gScores.put(start, 0f);
        allNodes.put(start, startNode);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();

            // If we reached the goal, reconstruct the path
            if (current.id.equals(goal)) {
                return reconstructPath(current);
            }


            // Explore neighbors
            for (String neighbor : connections.getOrDefault(current.id, new ArrayList<>())) {
                float[] currentPos = landmarkPositions.get(current.id);
                float[] neighborPos = landmarkPositions.get(neighbor);

                if (currentPos == null || neighborPos == null) {
                    Log.e("A*Error", "❌ Null position! current: " + current.id + " → " + Arrays.toString(currentPos) +
                            ", neighbor: " + neighbor + " → " + Arrays.toString(neighborPos));
                    continue; // Skip this neighbor if position data is missing
                }

                float tentativeG = current.gCost + distance(currentPos, neighborPos);

                if (!gScores.containsKey(neighbor) || tentativeG < gScores.get(neighbor)) {
                    gScores.put(neighbor, tentativeG);
                    Node neighborNode = new Node(neighbor, tentativeG, tentativeG + heuristic(neighborPos, landmarkPositions.get(goal)), current);
                    openSet.add(neighborNode);
                    allNodes.put(neighbor, neighborNode);
                }
            }
        }
        return null;
        }


            private static List<String> reconstructPath(Node node) {
        List<String> path = new ArrayList<>();
        while (node != null) {
            path.add(0, node.id); // Add to front (reversing the path)
            node = node.parent;
        }
        return path;
    }

    private static float heuristic(float[] pos1, float[] pos2) {
        return distance(pos1, pos2); // Euclidean distance
    }

    private static float distance(float[] pos1, float[] pos2) {
        return (float) Math.sqrt(Math.pow(pos2[0] - pos1[0], 2) + Math.pow(pos2[1] - pos1[1], 2) + Math.pow(pos2[2] - pos1[2], 2));
    }


}
