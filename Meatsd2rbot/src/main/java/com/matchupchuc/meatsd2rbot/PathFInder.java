import java.util.List;

using System;
using System.Collections.Generic;
using System.IO;

public class PathFinder
{
    private static readonly string LOD_PATH = @"C:\DiabloII"; // Replace with your D2 LOD 1.13c path
    private Dictionary<int, int[,]> mapData = new Dictionary<int, int[,]>();

    public PathFinder()
    {
        LoadMapData();
    }

    private void LoadMapData()
    {
        // Chaos Sanctuary = Map ID 108 in D2 LOD
        string mapFile = Path.Combine(LOD_PATH, "data", "global", "maps", "108.map");
        try
        {
            using (var fs = new FileStream(mapFile, FileMode.Open, FileAccess.Read))
            using (var br = new BinaryReader(fs))
            {
                // Placeholder: Assume 500x500 grid
                int width = 500;
                int height = 500;
                int[,] grid = new int[width, height];
                // TODO: Parse real .map file format
                for (int x = 0; x < width; x++)
                    for (int y = 0; y < height; y++)
                        grid[x, y] = 0; // All walkable for now
                mapData[108] = grid;
            }
        }
        catch (IOException ex)
        {
            Console.WriteLine(ex.Message);
        }
    }

    public List<int[]> FindPath(int startX, int startY, int endX, int endY, int mapId)
    {
        if (!mapData.ContainsKey(mapId)) return null;
        int[,] grid = mapData[mapId];

        var open = new PriorityQueue<Node>((a, b) => a.F - b.F);
        var closed = new HashSet<Node>();
        var start = new Node(startX, startY, 0, Heuristic(startX, startY, endX, endY));
        open.Enqueue(start);

        while (open.Count > 0)
        {
            var current = open.Dequeue();
            if (current.X == endX && current.Y == endY)
                return ReconstructPath(current);
            closed.Add(current);

            int[][] dirs = { new[] { 0, 1 }, new[] { 1, 0 }, new[] { 0, -1 }, new[] { -1, 0 } };
            foreach (var dir in dirs)
            {
                int nx = current.X + dir[0];
                int ny = current.Y + dir[1];
                if (nx < 0 || nx >= grid.GetLength(0) || ny < 0 || ny >= grid.GetLength(1) || grid[nx, ny] == 1) continue;

                var neighbor = new Node(nx, ny, current.G + 1, Heuristic(nx, ny, endX, endY));
                neighbor.Parent = current;
                if (closed.Contains(neighbor)) continue;
                if (!open.Contains(neighbor)) open.Enqueue(neighbor);
            }
        }
        return null;
    }

    private int Heuristic(int x1, int y1, int x2, int y2)
    {
        return Math.Abs(x1 - x2) + Math.Abs(y1 - y2);
    }

    private List<int[]> ReconstructPath(Node end)
    {
        var path = new List<int[]>();
        var current = end;
        while (current != null)
        {
            path.Add(new[] { current.X, current.Y });
            current = current.Parent;
        }
        path.Reverse();
        return path;
    }

    private class Node
    {
        public int X, Y, G, F;
        public Node Parent;
        public Node(int x, int y, int g, int h) { X = x; Y = y; G = g; F = g + h; }
        public override bool Equals(object obj) => obj is Node n && X == n.X && Y == n.Y;
        public override int GetHashCode() => HashCode.Combine(X, Y);
    }

    // Simple priority queue (replace with System.Collections.Generic if needed)
    private class PriorityQueue<T>
    {
        private List<T> items = new List<T>();
        private Comparison<T> compare;
        public PriorityQueue(Comparison<T> comparison) { compare = comparison; }
        public void Enqueue(T item) { items.Add(item); items.Sort(compare); }
        public T Dequeue() { var item = items[0]; items.RemoveAt(0); return item; }
        public int Count => items.Count;
        public bool Contains(T item) => items.Contains(item);
    }
}