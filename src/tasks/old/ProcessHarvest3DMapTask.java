package tasks.old;

import gisUI.DragSupport;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.DoubleStream;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SubScene;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Path;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;
import javafx.util.Duration;

import org.geotools.data.FileDataStore;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.opengis.feature.Feature;
import org.opengis.feature.simple.SimpleFeature;

import tasks.ProcessMapTask;
import utils.ProyectionConstants;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.TopologyException;
import com.vividsolutions.jts.index.quadtree.Quadtree;

import dao.LaborItem;
import dao.config.Configuracion;
import dao.cosecha.CosechaItem;

public class ProcessHarvest3DMapTask extends Process3DMapTask {
	MeshView meshView = new MeshView();
	PerspectiveCamera camera = new PerspectiveCamera(true);           
	PointLight light2 = new PointLight();
	 private final Rotate rotateY =new Rotate(0, Rotate.Z_AXIS);//new Rotate(180, 0, 0, 0, Rotate.Y_AXIS);
	 private final Rotate rotateX =new Rotate(0, Rotate.X_AXIS);//new Rotate(180, 0, 0, 0, Rotate.Y_AXIS);
	
	public ProcessHarvest3DMapTask(Group mapa, Quadtree harvestTree ) {
		super.map = mapa;
		super.featureTree = harvestTree;


	AmbientLight ambientLight = new AmbientLight(Color.WHITE);
	ambientLight.setOpacity(0.5);
	

	Image imgPalette = new WritableImage(40, 40);
	PixelWriter pw = ((WritableImage)imgPalette).getPixelWriter();
	//AtomicInteger count = new AtomicInteger();
	IntStream.range(0, 40).boxed()
	        .forEach(y->IntStream.range(0, 40).boxed()
	        		.forEach(x->pw.setColor(x, y, ProcessMapTask.colors[11*x/40])));
	    	
	        //        .forEach(x->pw.setColor(x, y, Color.hsb(count.getAndIncrement()/1600*360,1,1))));
	
	final PhongMaterial redMaterial = new PhongMaterial();
//	redMaterial.setSpecularColor(Color.ORANGE);
//	redMaterial.setDiffuseColor(Color.RED);
	redMaterial.setDiffuseMap(imgPalette);
//	redMaterial.setDiffuseMap(new Image(getClass().getResourceAsStream("colors.png")));
	//center =(-6898742.119516514,-3767943.790121996)
	//Zmin =(-3767908.197980285)
	Sphere sol = new Sphere(5);
	sol.setMaterial(redMaterial);
	sol.setTranslateX(-6898742.119516514);//center =(-6898742.119516514,-1883897.5914901425)
	sol.setTranslateY(-3767943.790121996);
	

	
	meshView.setMaterial(redMaterial);
	Group subRoot = new Group();
	subRoot.getChildren().add(camera);
	subRoot.getChildren().add(light2);
	subRoot.getChildren().add(sol);
	subRoot.getChildren().add(meshView);

	
	SubScene subScene = new SubScene(subRoot, 1080, 920);
	subScene.setFill(Color.ALICEBLUE);
	subScene.setCamera(camera);
	camera.getTransforms().addAll( rotateY,rotateX);
	  DragSupport dragSupport1 = new DragSupport(subScene, null, Orientation.HORIZONTAL, rotateY.angleProperty());
	  DragSupport dragSupport2 = new DragSupport(subScene, null, Orientation.VERTICAL, rotateX.angleProperty());

	super.map.getChildren().add(subScene);
	}

	//TODO recorrer el featureTree creando los points para el mesh	
	public void doProcess() throws IOException {
		@SuppressWarnings("unchecked")
		List<CosechaItem> features = super.featureTree.queryAll();
		
		List<Point3D> points = new ArrayList<>();
		List<Point3D> triangles = new ArrayList<>();
		List<Integer> textureCoords =  new ArrayList<>();//para cada triangulo la textura es la misma
	
		Double minX=null,maxX=null,minY=null,maxY=null,minZ=null,maxZ=null;
		//crear los puntos 
		for(CosechaItem ci : features){
			Coordinate[] coords = ci.getGeometry().getCoordinates();
			List<Point3D> pointsCi = new ArrayList<>();
			for(Coordinate c: coords){
				double x = c.x ;
				double z = c.y;
				double y = ci.getElevacion()*50*ProyectionConstants.metersToLongLat;
				minX=minX==null?x:Math.min(x, minX);
				minY=minY==null?y:Math.min(y, minY);
				minZ=minZ==null?z:Math.min(z, minZ);
				
				maxX=maxX==null?x:Math.max(x, maxX);
				maxY=maxY==null?y:Math.max(y, maxY);
				maxZ=maxZ==null?z:Math.max(z, maxZ);				
				System.out.println("agregando el punto ("+x+", "+y+" ,"+z+")");
				pointsCi.add(new Point3D(x,y,z));
				
			}
			pointsCi.remove(pointsCi.size()-1);//quito el ultimo que es igual al primero
			points.addAll(pointsCi);
			
			// recorrer los puntos y generar los triangulos
		
			for(int i = 2 ; i<pointsCi.size(); i++){
				int p00 = points.indexOf(pointsCi.get(0));
				int p01 = points.indexOf(pointsCi.get(i-1));
				int p02 = points.indexOf(pointsCi.get(i));
				 triangles.add(new Point3D(p00,p01,p02));  
				 textureCoords.add(getTextureFor(ci.getAmount()));
			}			
		}
		
		   AtomicInteger count=new AtomicInteger();		
		   
		   int faces[] =  triangles.stream().map(f->{
	            int p0=(int)f.getX(); 
	            int p1=(int)f.getY();
	            int p2=(int)f.getZ();
	            Integer t=textureCoords.get(count.getAndIncrement());	          
	            return IntStream.of(p0, t, p1, t, p2, t);
	        }).flatMapToInt(i->i).toArray();
		   
		   
		   float[] floatVertices= new float[points.size()*3];
		   for(int i=0;i<points.size();i++){
			 Point3D  p = points.get(i);
			   floatVertices[i*3]=(float) p.getX();
			   floatVertices[i*3+1]=(float) p.getY();
			   floatVertices[i*3+2]=(float) p.getZ();			   
		   }
			
		   System.out.println("floatVertices size = "+floatVertices.length);
		   System.out.println("textureCoords size = "+textureCoords.size());
		   System.out.println("faces size = "+faces.length);
				
		TriangleMesh triangleMesh = new TriangleMesh();
		triangleMesh.getPoints().setAll(floatVertices);
		//WARNING: texCoords.size() has to be divisible by getTexCoordElementSize(). It is to store multiple u and v texture coordinates of this mesh
		triangleMesh.getTexCoords().setAll(getTexturePaletteArray());		
		triangleMesh.getFaces().setAll(faces);
	
		try{
			
		meshView.setMesh(triangleMesh);
		meshView.setCullFace(CullFace.NONE);
		}catch(Exception e){
		e.printStackTrace();	
		}
	//	meshView.setRotationAxis(arg0);
	//	meshView.getTransforms().add(new Rotate(30,Rotate.X_AXIS));
		
		Double centerX =(minX+maxX)/2;
		Double centerY =(minY+maxY)/2;
		Double centerZ =(minZ+maxZ)/2;
		
		
		
		light2.getTransforms().addAll (
				camera.getTransforms());
		
		camera.setTranslateX(centerX);
		camera.setTranslateY(centerY);//centerY);
		camera.setTranslateZ(centerZ-100*ProyectionConstants.metersToLongLat);
		double angle =0;
		
		 
		
		//matrixRotateNode(meshView,45,-20,0);
		
		//meshView.setRotationAxis(new Point3D(centerX,centerY,centerZ));
		//meshView.getTransforms().addAll(new Rotate(5,Rotate.X_AXIS));
		//camera.getTransforms().addAll(new Rotate(90,Rotate.X_AXIS));
	//	camera.setRotationAxis(new Point3D(centerX,centerY,centerZ));
	//	camera.getTransforms().addAll(new Rotate(20,Rotate.X_AXIS));
		
		
		 Affine affineIni=new Affine();            
         affineIni.prepend(new Rotate(-90, Rotate.X_AXIS));
         affineIni.prepend(new Rotate(90, Rotate.Z_AXIS));
         
		camera.setFarClip(50000);
		camera.setNearClip(0.1);
		System.out.println("Width =("+minX+","+maxX+")");
		System.out.println("Height =("+minZ+","+maxZ+")");
		System.out.println("center =("+centerX+","+centerZ+")");
		System.out.println("maxY =("+maxY+")");
//		camera.getTransforms().addAll (
//				new Rotate(-20, Rotate.X_AXIS),
//				new Rotate(0, Rotate.Y_AXIS),
//				new Rotate(0, Rotate.Z_AXIS),
//				new Translate(centerX, centerY, -300)
//				);
		
	//	meshView.setMaterial(greenMaterial);
	}
	
	 private void matrixRotateNode(Node n, double alf, double bet, double gam){
	        double A11=Math.cos(alf)*Math.cos(gam);
	        double A12=Math.cos(bet)*Math.sin(alf)+Math.cos(alf)*Math.sin(bet)*Math.sin(gam);
	        double A13=Math.sin(alf)*Math.sin(bet)-Math.cos(alf)*Math.cos(bet)*Math.sin(gam);
	        double A21=-Math.cos(gam)*Math.sin(alf);
	        double A22=Math.cos(alf)*Math.cos(bet)-Math.sin(alf)*Math.sin(bet)*Math.sin(gam);
	        double A23=Math.cos(alf)*Math.sin(bet)+Math.cos(bet)*Math.sin(alf)*Math.sin(gam);
	        double A31=Math.sin(gam);
	        double A32=-Math.cos(gam)*Math.sin(bet);
	        double A33=Math.cos(bet)*Math.cos(gam);
	         
	        double d = Math.acos((A11+A22+A33-1d)/2d);
	        if(d!=0d){
	            double den=2d*Math.sin(d);
	            Point3D p= new Point3D((A32-A23)/den,(A13-A31)/den,(A21-A12)/den);
	            n.setRotationAxis(p);
	            n.setRotate(Math.toDegrees(d));                    
	        }
	    }
	 
private float[] getTexturePaletteArray() {
	float[] pallete = new float[11*2];
//	{ 
//			0.1f, 0.5f, // 0 red
//      0.3f, 0.5f, // 1 green
//      0.5f, 0.5f, // 2 blue
//      0.7f, 0.5f, // 3 yellow
//      0.9f, 0.5f  // 4 orange
//	};
	for(int i =0;i<pallete.length;i+=2){
		pallete[i]=(float)i/22;
		pallete[i+1]=0.5f;
	//	System.out.println("pallete ["+i+"] es "+pallete[i]);
	}
	
	//float[] pallete = {0.5f,0.6f,0.6f,0.6f};//Tiene que ser multiplo de 2 para guardar u y v

		return pallete;
	}

//TODO crear un metodo que devuelva el indice de la textura asociada con el valor amount
	private Integer getTextureFor(Double amount) {
		Integer t = ProcessMapTask.getCategoryFor(amount);
//		System.out.println("texture for "+amount+" is "+t);
		return t;
	//	return 1;
	}

	protected int getAmountMin() {
		return 3;
	}

	protected int gerAmountMax() {
		return 15;
	}
}// fin del task