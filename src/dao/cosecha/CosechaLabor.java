package dao.cosecha;

import java.util.List;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.geotools.data.FileDataStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.AttributeType;

import dao.Labor;
import dao.LaborConfig;
import dao.LaborItem;
import dao.config.Configuracion;
import dao.config.Cultivo;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.Data;
import utils.ProyectionConstants;

@Data
@Entity @Access(AccessType.FIELD)
public class CosechaLabor extends Labor<CosechaItem> {
	private static final int KG_POR_TN = 1000;
	public static final double FEET_TO_METERS = 0.3048;
	private static final double KG_POR_LIBRA = 0.453592;

	public static final String COLUMNA_VELOCIDAD = "Velocidad";
	public static final String COLUMNA_RENDIMIENTO = "Rendimient";
	public static final String COLUMNA_DESVIO_REND = "DesvRendim";

	public static final String COLUMNA_PRECIO = "precio_gra";
	public static final String COLUMNA_IMPORTE_HA = "importe_ha";


	private static final String PRECIO_GRANO = "precioGrano";
	private static final String CORRECCION_COSECHA = "CORRECCION_COSECHA";
	private static final String COSTO_COSECHA_TN = "COSTO_COSECHA_TN";
	private static final String COSTO_COSECHA_HA = "COSTO_COSECHA_HA";

	private static final String MAX_RINDE_KEY = "MAX_RINDE";
	private static final String MIN_RINDE_KEY = "MIN_RINDE";

	private static final String PRODUCTO_DEFAULT = "CultivoDefault";
	private static final String COLUMNA_COSTO_LB_HA = "CostoLbTn";
	private static final String COLUMNA_COSTO_LB_TN = "CostoLbHa";

	@Transient 
	public StringProperty colRendimiento= null;
	@Transient 
	public Property<Cultivo> producto=null;//FIXME producto no se puede guardar como una property
	@Transient 
	public SimpleDoubleProperty precioGranoProperty= null;
	@Transient 
	public SimpleDoubleProperty costoCosechaTnProperty= null;

	//TODO mover a tasks separados de la cosecha
	@Transient 
	public SimpleDoubleProperty correccionCosechaProperty= null;//es el porcentaje de 0-100 por que que hay que multiplicar el rinde 
	@Transient 
	public SimpleDoubleProperty maxRindeProperty= null;
	@Transient 
	public SimpleDoubleProperty minRindeProperty= null;

	/**
	 * constructor que sirve para crear una cosecha artificial cuando no tiene
	 * un datastore que la represente
	 */
	public CosechaLabor() {
		super();
		initConfig();
	}

	public CosechaLabor(FileDataStore store) {
		super(store);
		initConfig();
	}

	//XXX ver como los listeners de las propiedades me afectan el archivo de properties y 
	//el controller de la configuracion. creo que setea las variables pero nunca las graba a menos 
	//que las grabe el controller
	protected void initConfig() {
		System.out.println("iniciando la configuracion de CosechLabor");
		List<String> availableColums = this.getAvailableColumns();		
		Configuracion properties = getConfigLabor().getConfigProperties();

		colRendimiento = initStringProperty(CosechaLabor.COLUMNA_RENDIMIENTO, properties, availableColums);
		//como detecto que es una cosecha default evito hacer correcciones de flow y de distancia
		if(CosechaLabor.COLUMNA_RENDIMIENTO.equals(colRendimiento.get())){
			this.getConfiguracion().correccionFlowToRindeProperty().setValue(false);
			this.getConfiguracion().correccionRindeProperty().setValue(false);
			this.getConfiguracion().valorMetrosPorUnidadDistanciaProperty().set(1);
		}
		colAmount= new SimpleStringProperty(CosechaLabor.COLUMNA_RENDIMIENTO);//Siempre tiene que ser el valor al que se mapea segun el item para el outcollection

		correccionCosechaProperty = initDoubleProperty(CosechaLabor.CORRECCION_COSECHA, "100", properties);
		minRindeProperty = initDoubleProperty(CosechaLabor.MIN_RINDE_KEY, "0", properties);
		maxRindeProperty = initDoubleProperty(CosechaLabor.MAX_RINDE_KEY, "0", properties);
		precioGranoProperty = initDoubleProperty(CosechaLabor.PRECIO_GRANO, "0", properties);
		costoCosechaTnProperty = initDoubleProperty(CosechaLabor.COSTO_COSECHA_TN, "0", properties);

		String productoKEY = properties.getPropertyOrDefault(CosechaLabor.PRODUCTO_DEFAULT, Cultivo.MAIZ);
		producto = new SimpleObjectProperty<Cultivo>(Cultivo.cultivos.get(productoKEY));//values().iterator().next());
		producto.addListener((obs, bool1, bool2) -> {
			properties.setProperty(CosechaLabor.PRODUCTO_DEFAULT,bool2.getNombre());
		});
	}

	@Override
	protected DoubleProperty initPrecioLaborHaProperty(){
		return initDoubleProperty(CosechaLabor.COSTO_COSECHA_HA,"0",config.getConfigProperties());
	} 
	
	@Override
	protected DoubleProperty initPrecioInsumoProperty() {
		return initDoubleProperty(CosechaLabor.PRECIO_GRANO,  "0", config.getConfigProperties());
	//	return initDoubleProperty(FertilizacionLabor.COSTO_LABOR_FERTILIZACION,"0",config.getConfigProperties());
	}

	@Override
	public String getTypeDescriptors() {
		String type = CosechaLabor.COLUMNA_RENDIMIENTO + ":Double,"	
				+CosechaLabor.COLUMNA_DESVIO_REND + ":Double,"	
				+CosechaLabor.COLUMNA_COSTO_LB_HA + ":Double,"	
				+CosechaLabor.COLUMNA_COSTO_LB_TN + ":Double,"	
				//+CosechaLabor.COLUMNA_VELOCIDAD + ":Double,"	
				+ CosechaLabor.COLUMNA_PRECIO + ":Double,"
				+CosechaLabor.COLUMNA_IMPORTE_HA+":Double";
		return type;
	}



	public void changeFeature(SimpleFeature old, CosechaItem ci) {
		outCollection.remove(old);
		outCollection.add(ci.getFeature(featureBuilder));
	}

	@Override
	public CosechaItem constructFeatureContainer(SimpleFeature harvestFeature) {
		CosechaConfig config = (CosechaConfig) super.config;
		CosechaItem ci = new CosechaItem(harvestFeature);
		super.constructFeatureContainer(ci,harvestFeature);

		Double rindeDouble = LaborItem.getDoubleFromObj(harvestFeature.getAttribute(colRendimiento.get()));

		if(this.getConfiguracion().correccionAnchoEnabled()){			
			ci.setAncho(this.anchoDefaultProperty.doubleValue());
		} 

		if (config.correccionFlowToRindeProperty().getValue()) {
			//TODO si existe el campo Moisture_s tomarlo encuenta para secar el grano

			/*
			 * mass_flow[kg/s]=0.4535*lb/s ancho[m] velocidad[m/s] rinde[kg/ha] =
			 * 10000*mass_flow/(ancho*velocidad)
			 */

			// ("Mass_Flow_" *0.453592) /((("Width" *2.54/100)*
			// ("Distance"*2.54/100))/10000)
			/*
			 * convertir el rinde que es un flow en libras por segundo a kg por //o es en bushel??
			 * ha. para eso hay que usar la formula rinde = flow*[kg por
			 * libra]*[1 segundo]*[m2 por Ha]/(width*distance)
			 */
			double constantes = ProyectionConstants.METROS2_POR_HA
					* KG_POR_LIBRA/KG_POR_TN;// XXX asume un dato por segundo
			//si la distancia o el ancho son cero esto da infinito
			double divisor = ci.getDistancia() * ci.getAncho();
			if(divisor>0){
				rindeDouble = rindeDouble * constantes / (divisor);
			} 
//			else {
//				rindeDouble =rindeDouble;
//			}
			
			List<AttributeType> descriptors = harvestFeature.getType().getTypes();
			String moistureColumn =null;
			for(AttributeType att:descriptors){
			String colName = att.getName().toString();
				 if("Moisture_s".equalsIgnoreCase(colName)){
					moistureColumn =colName;	
				}
			}
			
			if(moistureColumn!=null){
				Double moisture =LaborItem.getDoubleFromObj(harvestFeature.getAttribute(moistureColumn));
				Double rindeH = ci.getRindeTnHa();
				Double k = 100/(100+moisture);
				ci.setRindeTnHa(rindeH*k);
			}
			
			//CAMBIO LA ELEVACION DE PIES A METROS
			String convertir =this.config.getConfigProperties().getPropertyOrDefault("ConvertirElevacionPiesAMetros", "true");
			if("true".equals(convertir)){
				//double feetToMeters =;
				ci.setElevacion(ci.getElevacion()*FEET_TO_METERS);
				
			}
		}

		double correccionRinde = correccionCosechaProperty.doubleValue()/100;
		ci.rindeTnHa = rindeDouble * (correccionRinde);		
		setPropiedadesLabor(ci);
		return ci;
	}

	public void setPropiedadesLabor(CosechaItem ci){
		ci.precioTnGrano =this.precioGranoProperty.get();
		ci.costoLaborHa=this.precioLaborProperty.get();
		ci.costoLaborTn=this.costoCosechaTnProperty.get();
	}
	
	@Override
	public  CosechaItem constructFeatureContainerStandar(
			SimpleFeature harvestFeature,boolean newIDS) {
		CosechaItem ci = new CosechaItem(harvestFeature);
		super.constructFeatureContainerStandar(ci,harvestFeature,newIDS);
		ci.rindeTnHa = LaborItem.getDoubleFromObj(harvestFeature
				.getAttribute(CosechaLabor.COLUMNA_RENDIMIENTO));
		ci.desvioRinde = LaborItem.getDoubleFromObj(harvestFeature
				.getAttribute(CosechaLabor.COLUMNA_DESVIO_REND));

		setPropiedadesLabor(ci);//para que se actualice si se edito la labor
//		ci.precioTnGrano = LaborItem.getDoubleFromObj(harvestFeature
//				.getAttribute(CosechaLabor.COLUMNA_PRECIO));
//
//		ci.costoLaborHa=LaborItem.getDoubleFromObj(harvestFeature
//				.getAttribute(CosechaLabor.COLUMNA_COSTO_LB_HA));
//		ci.costoLaborTn=LaborItem.getDoubleFromObj(harvestFeature
//				.getAttribute(CosechaLabor.COLUMNA_COSTO_LB_TN));

		return ci;
	}

	//se usa en los tasks
	public CosechaConfig getConfiguracion() {
		if(config==null){
			config = new CosechaConfig();
		}
		return (CosechaConfig) config;
	}

	@Override
	public LaborConfig getConfigLabor() {
		return getConfiguracion();
	}
}
