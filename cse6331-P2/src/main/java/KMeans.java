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
		super();
		this.x = x;
		this.y = y;
	}
	public Point() {
		super();
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

class AverageMapper implements WritableComparable {
	Double xSum=0.0,ySum=0.0;
	int count =0;
	public AverageMapper() {
		xSum=0.0;
		ySum=0.0;
		count=0;
	}
	public AverageMapper(Double xSum, Double ySum, int count) {
		this.xSum = xSum;
		this.ySum = ySum;
		this.count = count;
	}
	public Double getxSum() {
		return xSum;
	}
	public void setxSum(Double xSum) {
		this.xSum = xSum;
	}
	public Double getySum() {
		return ySum;
	}
	public void setySum(Double ySum) {
		this.ySum = ySum;
	}
	public int getCount() {
		return count;
	}
	public void setCount(int count) {
		this.count = count;
	}
	@Override
	public void write(DataOutput out) throws IOException {
		out.writeDouble(xSum);
		out.writeDouble(ySum);
		out.writeInt(count);

	}
	@Override
	public void readFields(DataInput in) throws IOException {
		xSum=in.readDouble();
		ySum=in.readDouble();
		count=in.readInt();

	}
	@Override
	public int compareTo(Object o) {
		if(this.count>0) 
			return 1;
		else
			return-1;
	}

}

public class KMeans {
	//contains all the centroids
	static Vector<Point> centroids = new Vector<Point>(100);
	//contains all the mapped points to centroids
	static HashMap<Point, AverageMapper> avgMappers = new HashMap<Point, AverageMapper>();

	//	Mapper class
	public static class AvgMapper extends Mapper<Object,Text,Point,AverageMapper> {


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
		protected void cleanup(Mapper<Object,Text,Point,AverageMapper>.Context context)
				throws IOException, InterruptedException {
			// export Hash Table to reducer
			for (Point p : avgMappers.keySet()) {
				context.write(p, avgMappers.get(p));
			}
			avgMappers.clear();
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

			// collect centroids and points associated with that centroid
			if(avgMappers.containsKey(chosenCentroid)) {
				avgMappers.get(chosenCentroid).xSum+=point.x;
				avgMappers.get(chosenCentroid).ySum+=point.y;
				avgMappers.get(chosenCentroid).count+=1;
			}else {
				avgMappers.put(chosenCentroid, new AverageMapper(point.x,point.y,1));
			}
			pointScanner.close();
		}    	
	}

	public static class AvgReducer extends Reducer<Point,AverageMapper,Point,Object> {


		@Override
		protected void reduce(Point centroid, Iterable<AverageMapper> arg1, Context context)
				throws IOException, InterruptedException {
			Iterator<AverageMapper> pointIterator = arg1.iterator();
			int count=0;
			Double xSum=0.0,ySum=0.0,xAvg=0.0,yAvg=0.0;

			// loop through and get the total sum and total count of each centroid point 
			while(pointIterator.hasNext()) {
				AverageMapper avg = pointIterator.next();
				xSum += avg.xSum;
				ySum += avg.ySum;
				count+=avg.count;
			}
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

		job.setMapOutputValueClass(AverageMapper.class);
		job.setOutputValueClass(Point.class);

		job.setInputFormatClass(TextInputFormat.class); 	
		job.setOutputFormatClass(TextOutputFormat.class);

		//Set Input and Output Paths
		FileOutputFormat.setOutputPath(job,new Path(args[2]));
		FileInputFormat.setInputPaths(job,new Path(args[0]));
		job.waitForCompletion(true);
	}
}
