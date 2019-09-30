# KMeans-Clustering
Project to show the working of KMeans clustering though Map-Reduce Framework.

## Project Description
This project implements one step of the Lloyd's algorithm for k-means clustering. The goal is to partition a set of points into k clusters of neighboring points. It starts with an initial set of k centroids. Then, it repeatedly partitions the input according to which of these centroids is closest and then finds a new centroid for each partition. That is, if you have a set of points P and a set of k centroids C, the algorithm repeatedly applies the following steps:

**Assignment step:** partition the set P into k clusters of points Pi, one for each centroid Ci, such that a point p belongs to Pi if it is closest to the centroid Ci among all centroids.
**Update step:** Calculate the new centroid Ci from the cluster Pi so that the x,y coordinates of Ci is the mean x,y of all points in Pi.
        
The datasets used are random points on a plane in the squares (i*2+1,j*2+1)-(i*2+2,j*2+2), with 0≤i≤9 and 0≤j≤9 (so k=100 in k-means). The initial centroids in centroid.txt are the points (i*2+1.2,j*2+1.2). So the new centroids should be in the middle of the squares at (i*2+1.5,j*2+1.5).

### *Pseudo Code*

class Point {
        public double x;        
        public double y;     
        }

Vector[Point] centroids;

*mapper setup:

  read centroids from the distributed cache

**map ( key, line ):**

  Point p = new Point()
  
  read 2 double numbers from the line (x and y) and store them in p
  
  find the closest centroid c to p
  
  emit(c,p)
    
**reduce ( c, points ):**

  count = 0
  
  sx = sy = 0.0
  
  for p in points
  
      count++
      sx += p.x
      sy += p.y
      
  c.x = sx/count
  
  c.y = sy/count
  
  emit(c,null)*
  
  
  
