import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;
import java.util.Vector;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.*;
import org.apache.hadoop.mapreduce.lib.output.*;


class Point implements WritableComparable<Point> {
	//x,y coordinates
	public double x;
	public double y;

	public Point(double x, double y) {
		this.x = x;
		this.y = y;
	}
	public Point() {
		this.x = 0;
		this.y = 0;
	}
	public void write(DataOutput out) throws IOException {
		out.writeDouble(x);
		out.writeDouble(y);
	}
	public void readFields(DataInput in) throws IOException {
		x 	=	in.readDouble();
		y	=	in.readDouble();
	}
	public int compareTo(Point p) {
		int returnVal=0;
		
		if(this.x==p.x)
			returnVal= (int)(this.y-p.y);
		else
			returnVal= (int)(this.x-p.x);
		return returnVal;
	}
	@Override
	public String toString() {
		return x + ", " + y ;
	}
}

public class KMeans {
	//contains all the centroids
	static Vector<Point> centroids = new Vector<Point>(100);
	//	Mapper class
	public static class AvgMapper extends Mapper<Object,Text,Point,Point> {


		@Override
		protected void setup(Context context)
				throws IOException, InterruptedException {
			// Read all the centroids from the file to the Vector
			URI[] paths = context.getCacheFiles();
			Configuration conf = context.getConfiguration();
			FileSystem fs = FileSystem.get(conf);
			BufferedReader reader = new BufferedReader(new InputStreamReader(fs.open(new Path(paths[0]))));
			String str="";
			while ((str = reader.readLine())!=null) {
				// split by ","
				String strXY[] = str.split(",");
				centroids.add(new Point(Double.parseDouble(strXY[0]), Double.parseDouble(strXY[1])));
			}
		}
		@Override
		protected void map(Object key, Text value, Context context)
				throws IOException, InterruptedException {
			Scanner pointScanner = new Scanner(value.toString()).useDelimiter(",");
			//	get the x,y coordinates of the point
			Point point = new Point(pointScanner.nextDouble(),pointScanner.nextDouble());
			//	find the least euclidean distance between point and centroid
			double distance = Double.MAX_VALUE;
			Point chosenCentroid=new Point();
			for(Point c:centroids) {
				//	euclidean Distance
				double currentDistance = Math.sqrt(Math.pow((point.x-c.x),2)+Math.pow((point.y-c.y),2));
				//	check if euclidean distance less than the previous value
				if(currentDistance<distance) {
					distance=currentDistance;
					chosenCentroid = c;
				}
			}				
			context.write(chosenCentroid, point);
			pointScanner.close();
		}    	
	}

	public static class AvgReducer extends Reducer<Point,Point,Point,Object> {


		@Override
		protected void reduce(Point centroid, Iterable<Point> arg1, Context context)
				throws IOException, InterruptedException {
			Iterator<Point> pointIterator = arg1.iterator();
			int count=0;
			Double xSum=0.0,ySum=0.0,xAvg=0.0,yAvg=0.0;

			// loop through and get the total sum and total count of each centroid point 
			while(pointIterator.hasNext()) {
				Point avg = pointIterator.next();
				xSum += avg.x;
				ySum += avg.y;
				count++;
			}
			System.out.println("COUNT: "+count);
			// get new Centroid coordinates
			xAvg = xSum/count;
			yAvg = ySum/count;

			Point newCentroid = new Point();
			newCentroid.x=xAvg;
			newCentroid.y=yAvg;

			//	emit new centroid
			context.write(newCentroid,null);
		}
	}

	public static void main ( String[] args ) throws Exception {
		Job job = Job.getInstance();
		job.setJobName("MapReduceADB");
		job.setJarByClass(KMeans.class);
		job.addCacheFile(new URI(args[1]));   

		//name reducer and mapper classes
		job.setReducerClass(AvgReducer.class);
		job.setMapperClass(AvgMapper.class);

		//Set output Key and value classes
		job.setMapOutputKeyClass(Point.class);
		job.setOutputKeyClass(Point.class);

		job.setMapOutputValueClass(Point.class);
		job.setOutputValueClass(Point.class);

		job.setInputFormatClass(TextInputFormat.class); 	
		job.setOutputFormatClass(TextOutputFormat.class);

		//Set Input and Output Paths
		FileOutputFormat.setOutputPath(job,new Path(args[2]));
		FileInputFormat.setInputPaths(job,new Path(args[0]));
		job.waitForCompletion(true);
	}
}
