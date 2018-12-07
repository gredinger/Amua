/**
 * Amua - An open source modeling framework.
 * Copyright (C) 2017 Zachary J. Ward
 *
 * This file is part of Amua. Amua is free software: you can redistribute
 * it and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Amua is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Amua.  If not, see <http://www.gnu.org/licenses/>.
 */

package export_Python;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import javax.swing.JOptionPane;

import base.AmuaModel;
import main.*;
import math.Constants;
import math.Distributions;
import math.Functions;
import math.Interpreter;
import math.MatrixFunctions;
import math.Numeric;
import math.NumericException;

public class PythonModel{
	String dir;
	BufferedWriter out;
	AmuaModel myModel;
	ArrayList<String> functionNames; //to export
	ArrayList<String> functionMethods;
	
	public PythonModel(String dir, BufferedWriter out, AmuaModel myModel){
		this.dir=dir;
		this.out=out;
		this.myModel=myModel;
		functionNames=new ArrayList<String>();
		functionMethods=new ArrayList<String>();
	}

	public void writeProperties(){
		try{
			writeLine(0,"# -*- coding: utf-8 -*-");
			writeLine(0,"\"\"\"");
			writeLine(0,"This code was auto-generated by Amua (https://github.com/zward/Amua)");
			writeLine(0,"Code generated: "+new Date());
			writeLine(0,"Model name: "+myModel.name);
			if(myModel.type==0){writeLine(0,"Model type: Decision Tree");}
			else if(myModel.type==1){writeLine(0,"Model type: Markov Model");}
			if(myModel.simType==0){writeLine(0,"Simulation type: Cohort");}
			else if(myModel.simType==1){writeLine(0,"Simulation type: Monte Carlo");}
			//metadata
			writeLine(0,"Created by: "+myModel.meta.author);
			writeLine(0,"Created: "+myModel.meta.dateCreated);
			writeLine(0,"Version created: "+myModel.meta.versionCreated);
			writeLine(0,"Modified by: "+myModel.meta.modifier);
			writeLine(0,"Modified: "+myModel.meta.dateModified);
			writeLine(0,"Version modified: "+myModel.meta.versionModified);
			writeLine(0,"\"\"\"");
		}catch(Exception e){
			recordError(e);
		}
	}


	public String translate(String expression, boolean personLevel) throws Exception{
		String curText=expression.replaceAll(" ", ""); //remove spaces
		String exportText="";
		//Parse expression word by word
		int len=curText.length();
		while(len>0){
			int pos=Interpreter.getNextBreakIndex(curText);
			String word=curText.substring(0, pos);
			String split="";
			if(pos<len){split=curText.substring(pos,pos+1);}
			
			if(myModel.isTable(word)){ //if table
				int tableIndex=myModel.getTableIndex(word);
				Table curTable=myModel.tables.get(tableIndex);
				if(curTable.type.matches("Lookup")){
					int close=Interpreter.findRightBracket(curText,pos);
					String args[]=Interpreter.splitArgs(curText.substring(pos+1,close));
					exportText+=curTable.name+".getLookupValue("+translate(args[0],personLevel)+","+curTable.getColumnIndex(args[1])+")";
					pos=close; //Move to end of table indices
				}
				else if(curTable.type.matches("Distribution")){ //Replace with value
					int close=Interpreter.findRightParen(curText,pos);
					String args[]=Interpreter.splitArgs(curText.substring(pos+1,close));
					exportText+=curTable.name+".calcEV("+curTable.getColumnIndex(args[0])+")";
					pos=close; //Move to end of dist parameters
				}
				else if(curTable.type.matches("Matrix")){
					if(pos<len && curText.charAt(pos)=='['){ //matrix index
						int close=Interpreter.findRightBracket(curText,pos);
						String args[]=Interpreter.splitArgs(curText.substring(pos+1,close));
						exportText+=curTable.name+"data["+translate(args[0],personLevel)+"]["+translate(args[1],personLevel)+"]";
						pos=close; //Move to end of matrix indices
					}
					else{ //entire matrix
						exportText+=curTable.name;
					}
				}
			}
			else if(myModel.isVariable(word)){ //Variable
				if(personLevel){ //individual-level
					exportText+="curPerson."+word+split;
				}
				else{
					exportText+=word+split;
				}
			}
			else if(word.matches("trace")){ //Markov Trace
				int close=Interpreter.findRightBracket(curText,pos);
				String args[]=Interpreter.splitArgs(curText.substring(pos+1,close));
				exportText+="trace.getValue("+translate(args[0],personLevel)+","+translate(args[1],personLevel)+")";
				pos=close; //Move to end of trace indices
			}
			else if(Functions.isFunction(word)){
				int close=Interpreter.findRightParen(curText, pos);
				String args[]=Interpreter.splitArgs(curText.substring(pos+1,close));
				int fxType=PythonFunctions.inPlace(word);
				if(fxType<2){
					//translate function call
					exportText+=PythonFunctions.translate(word)+"("+translate(args[0],personLevel);
					for(int i=1; i<args.length; i++){exportText+=","+translate(args[i],personLevel);}
					exportText+=")";
					pos=close+1; //Move to end of function call
					if(fxType==1){ //define method
						if(!functionNames.contains(word)){ //not defined yet
							functionNames.add(word);
							functionMethods.add(PythonFunctions.define(word));
						}
					}
				}
				else if(fxType==2){ //change args
					exportText+=PythonFunctions.changeArgs(word,args,this,personLevel);
					pos=close; //Move to end of function indices
				}
			}
			else if(MatrixFunctions.isFunction(word)){
				int close=Interpreter.findRightParen(curText, pos);
				String args[]=Interpreter.splitArgs(curText.substring(pos+1,close));
				int fxType=PythonMatrixFunctions.inPlace(word);
				if(fxType<2){
					//translate function call
					exportText+=PythonMatrixFunctions.translate(word)+"("+translate(args[0],personLevel);
					for(int i=1; i<args.length; i++){exportText+=","+translate(args[i],personLevel);}
					exportText+=")";
					pos=close+1; //Move to end of function call
					if(fxType==1){ //define method
						if(!functionNames.contains(word)){ //not defined yet
							functionNames.add(word);
							functionMethods.add(PythonMatrixFunctions.define(word));
						}
					}
				}
				else if(fxType==2){ //change args
					exportText+=PythonMatrixFunctions.changeArgs(word,args,this,personLevel);
					pos=close; //Move to end of function indices
				}
			}
			else if(Distributions.isDistribution(word)){
				//just parse arguments for now
				int close=Interpreter.findRightParen(curText,pos);
				String args[]=Interpreter.splitArgs(curText.substring(pos+1,close));
				exportText+=word+"("+translate(args[0],personLevel);
				for(int i=1; i<args.length; i++){
					exportText+=","+translate(args[i],personLevel);
				}
				exportText+=")";
				pos=close+1; //Move to end of distribution call
			}
			else if(Constants.isConstant(word)){
				exportText+=PythonConstants.translate(word)+split;
			}
			else if(curText.charAt(0)=='['){ //new defined matrix or vector
				int close=Interpreter.findRightBracket(curText,0);
				String strMatrix=curText.substring(1,close);
				Numeric matrix=Interpreter.parseMatrix(strMatrix,myModel,false);
				//write out
				if(matrix.nrow>1){exportText+=writeMatrix(matrix.matrix);}
				else{exportText+=writeArray(matrix.matrix[0]);}
				pos=close+1; //Move to end of matrix
			}
			
			else{ //not key word
				exportText+=word+split;
			}

			if(pos==len){len=0;} //End of word
			else{
				curText=curText.substring(pos+1);
				len=curText.length();
			}
		}

		return(exportText);
	}

	private String initNumeric(String name, Numeric value) throws NumericException{
		String init="";
		if(value.isDouble()){init=name+"="+value.getDouble();}
		else if(value.isInteger()){init=name+"="+value.getInt();}
		else if(value.isBoolean()){init=name+"="+value.getBool();}
		else if(value.isMatrix()){
			if(value.nrow>1){init=name+"="+writeMatrix(value.matrix);}
			else{init=name+"="+writeArray(value.matrix[0]);}
		}
		return(init);
	}
	
	public void writeParameters() throws NumericException{
		int numParams=myModel.parameters.size();
		if(numParams>0){
			writeLine(0,"#Define parameters");
			for(int i=0; i<numParams; i++){
				Parameter curParam=myModel.parameters.get(i);
				if(!curParam.notes.isEmpty()){
					writeLine(0,"\"\"\""+curParam.notes+"\"\"\"");
				}
				String expr=curParam.expression;
				String init=initNumeric(curParam.name,curParam.value);
				writeLine(0,init+" #Expression: "+expr);
			}
			writeLine(0,"");
		}
	}

	public void writeVariables() throws NumericException{
		int numVars=myModel.variables.size();
		if(numVars>0){
			writeLine(0,"#Define variables");
			for(int i=0; i<numVars; i++){
				Variable curVar=myModel.variables.get(i);
				if(!curVar.notes.isEmpty()){
					writeLine(0,"\"\"\""+curVar.notes+"\"\"\"");
				}
				String init=initNumeric(curVar.name,curVar.value);
				writeLine(0,init);
			}
			writeLine(0,"");
		}
	}
	
	private String writeArray(double array[]){
		int len=array.length;
		String write="np.array([";
		for(int i=0; i<len-1; i++){write+=array[i]+",";}
		write+=array[len-1]+"])";
		return(write);
	}
	
	private String writeMatrix(double matrix[][]){
		int nrow=matrix.length;
		int ncol=matrix[0].length;
		String write="np.matrix([";
		write+="[";
		for(int c=0; c<ncol-1; c++){write+=matrix[0][c]+",";}
		write+=matrix[0][ncol-1]+"]";
		for(int r=1; r<nrow; r++){
			write+=",\n[";
			for(int c=0; c<ncol-1; c++){write+=matrix[r][c]+",";}
			write+=matrix[r][ncol-1]+"]";
		}
		write+="])";
		return(write);
	}
	
	/**
	 * Defines any function methods needed
	 */
	public void defineFunctions(){
		try{
			int numFx=functionNames.size();
			if(numFx>0){
				FileWriter fstream = new FileWriter(dir+"functions.py"); //Create new file
				BufferedWriter outFx = new BufferedWriter(fstream);
				BufferedWriter temp=out;
				out=outFx;
				writeProperties();
				writeLine(0,"import math");
				writeLine(0,"import numpy as np");
				writeLine(0,"");
				for(int f=0; f<numFx; f++){
					outFx.write(functionMethods.get(f)); outFx.newLine();
					outFx.newLine();
				}
				outFx.close();
				out=temp; //point back
			}
		}catch(Exception e){
			recordError(e);
		}
	}
	
	private void writeTableSplines(Table curTable) throws IOException{
		int numSplines=curTable.splines.length;
		writeLine(0,curTable.name+".splines=[]"); out.newLine();
		for(int s=0; s<numSplines; s++){
			writeLine(0,curTable.name+"_knots_"+s+"="+writeArray(curTable.splines[s].knots));
			writeLine(0,curTable.name+"_knotHeights_"+s+"="+writeArray(curTable.splines[s].knotHeights));
			writeLine(0,curTable.name+"_splineCoeffs_"+s+"="+writeMatrix(curTable.splines[s].splineCoeffs));
			writeLine(0,curTable.name+".splines.append(CubicSpline("+curTable.name+"_knots_"+s+","+curTable.name+"_knotHeights_"+s+","+
			curTable.splines[s].numSplines+","+curTable.name+"_splineCoeffs_"+s+","+curTable.splines[s].boundaryCondition+"))");
		}
	}

	/**
	 * Defines a table class in Java
	 * @param out
	 * @throws IOException 
	 */
	public void writeTableClass(int tableFormat) throws IOException{
		int numTables=myModel.tables.size();
		if(numTables>0){
			FileWriter fstream = new FileWriter(dir+"Table.py"); //Create new file
			BufferedWriter outTable = new BufferedWriter(fstream);
			BufferedWriter origOut=out;
			out=outTable; //re-point
			
			writeLine(0,"class CubicSpline:");
				writeLine(1,"\"Cubic Spline class\"");
			writeLine(0,"");
				writeLine(1,"def __init__(self,knots,knotHeights,numSplines,splineCoeffs,boundaryCondition):");
					writeLine(2,"self.knots=knots");
					writeLine(2,"self.knotHeights=knotHeights");
					writeLine(2,"self.numSplines=numSplines");
					writeLine(2,"self.splineCoeffs=splineCoeffs");
					writeLine(2,"self.boundaryCondition=boundaryCondition");
			writeLine(0,"");
				writeLine(1,"def evaluate(self,x):");
					writeLine(2,"y=float('nan')");
					writeLine(2,"#Find domain");
					writeLine(2,"index=-1");
					writeLine(2,"if(x<self.knots[0]): #Extrapolate left");
						writeLine(3,"x=x-self.knots[0]");
						writeLine(3,"a=self.splineCoeffs[0]");
						writeLine(3,"if(self.boundaryCondition==0 or self.boundaryCondition==1): #Natural or clamped");
							writeLine(4,"slope=a[0,1]");
							writeLine(4,"y=slope*x+self.knotHeights[0]");
						writeLine(3,"else: #Not-a-knot or periodic");
							writeLine(4,"index=0");
							writeLine(4,"y=self.splineCoeffs[index,0]+self.splineCoeffs[index,1]*x+self.splineCoeffs[index,2]*x*x+self.splineCoeffs[index,3]*x*x*x");
					writeLine(2,"elif(x>self.knots[self.numSplines]): #Extrapolate right");
						writeLine(3,"a=self.splineCoeffs[self.numSplines-1]");
						writeLine(3,"if(self.boundaryCondition==0 or self.boundaryCondition==1): #Natural or clamped");
							writeLine(4,"x=x-self.knots[sel.numSplines]");
							writeLine(4,"h=self.knots[self.numSplines]-self.knots[self.numSplines-1]");
							writeLine(4,"slope=a[0,1]+2*a[0,2]*h+3*a[0,3]*h*h");
							writeLine(4,"y=slope*x+self.knotHeights[self.numSplines]");
						writeLine(3,"else: #Not-a-knot or periodic");
							writeLine(4,"index=self.numSplines-1");
							writeLine(4,"x=x-self.knots[index]");
							writeLine(4,"y=self.splineCoeffs[index,0]+self.splineCoeffs[index,1]*x+self.splineCoeffs[index,2]*x*x+self.splineCoeffs[index,3]*x*x*x");
					writeLine(2,"else: #Interpolate");
						writeLine(3,"index=0");
						writeLine(3,"while(x>self.knots[index+1] and index<self.numSplines-1):");
							writeLine(4,"index+=1");
						writeLine(3,"x=x-self.knots[index]");
						writeLine(3,"y=self.splineCoeffs[index,0]+self.splineCoeffs[index,1]*x+self.splineCoeffs[index,2]*x*x+self.splineCoeffs[index,3]*x*x*x");
					writeLine(2,"return(y)");
			writeLine(0,"");
			writeLine(0,"class Table:");
				writeLine(1,"\"Table class\"");
				writeLine(1,"name=\"\"");
				writeLine(1,"type=\"\"");
				writeLine(1,"lookupMethod=\"\"");
				writeLine(1,"interpolate=\"\"");
				writeLine(1,"boundary=\"\"");
				writeLine(1,"extrapolate=\"\"");
				writeLine(1,"numRows=0");
				writeLine(1,"numCols=0");
			writeLine(0,"");
			if(tableFormat==0){
				writeLine(1,"def __init__(self,name,type,lookupMethod,interpolate,boundary,extrapolate,headers,data):");
					writeLine(2,"self.name=name");
					writeLine(2,"self.type=type");
					writeLine(2,"self.lookupMethod=lookupMethod");
					writeLine(2,"self.interpolate=interpolate");
					writeLine(2,"self.boundary=boundary");
					writeLine(2,"self.extrapolate=extrapolate");
					writeLine(2,"self.headers=headers");
					writeLine(2,"self.data=data");
					writeLine(2,"self.numRows=len(data)");
					writeLine(2,"self.numCols=len(headers)");
					writeLine(2,"self.splines=[]");
			writeLine(0,"");
			}
			else if(tableFormat==1){
				writeLine(1,"def __init__(self,name,type,lookupMethod,interpolate,boundary,extrapolate,numRows,numCols,filepath):");
					writeLine(2,"self.name=name");
					writeLine(2,"self.type=type");
					writeLine(2,"self.lookupMethod=lookupMethod");
					writeLine(2,"self.interpolate=interpolate");
					writeLine(2,"self.boundary=boundary");
					writeLine(2,"self.extrapolate=extrapolate");
					writeLine(2,"self.numRows=numRows");
					writeLine(2,"self.numCols=numCols");
					writeLine(2,"self.splines=[]");
					writeLine(2,"#Read table");
					writeLine(2,"with open(filepath) as csvfile:");
						writeLine(3,"reader = csv.reader(csvfile, delimiter=',')");
						writeLine(3,"self.headers = next(reader)  #Headers");
						writeLine(3,"self.data=[]");
						writeLine(3,"for r in range(0,numRows):");
							writeLine(4,"strRow=next(reader)");
							writeLine(4,"curRow=[float(x) for x in strRow]");
							writeLine(4,"self.data.append(curRow)");
			writeLine(0,"");
			}
				writeLine(1,"def getLookupValue(self,index,col):");
					writeLine(2,"if(col<1 or col>(self.numCols-1)): #Invalid column");
						writeLine(3,"return(float('nan'))");
					writeLine(2,"else: #Valid column");
						writeLine(3,"val=float('nan')");
						writeLine(3,"if(self.lookupMethod==\"Exact\"):");
							writeLine(4,"row=-1");
							writeLine(4,"found=False");
							writeLine(4,"while(found==False and row<self.numRows):");
								writeLine(5,"row+=1");
								writeLine(5,"if(index==self.data[row][0]): found=True");
							writeLine(4,"if(found): val=self.data[row][col]");
						writeLine(3,"elif(self.lookupMethod==\"Truncate\"):");
							writeLine(4,"if(index<self.data[0][0]): val=float('nan') #Below first value - error");
							writeLine(4,"elif(index>=self.data[self.numRows-1][0]): val=self.data[self.numRows-1][col] #Above last value");
							writeLine(4,"else: #Between");
								writeLine(5,"row=0");
								writeLine(5,"while(self.data[row][0]<index): row+=1");
								writeLine(5,"if(index==self.data[row][0]): val=self.data[row][col]");
								writeLine(5,"else: val=self.data[row-1][col]");
						writeLine(3,"elif(self.lookupMethod==\"Interpolate\"):");
							writeLine(4,"if(self.interpolate==\"Linear\"):");
								writeLine(5,"if(index<=self.data[0][0]): #Below or at first index");
									writeLine(6,"slope=(self.data[1][col]-self.data[0][col])/(self.data[1][0]-self.data[0][0])");
									writeLine(6,"val=self.data[0][col]-(self.data[0][0]-index)*slope");
								writeLine(5,"elif(index>self.data[self.numRows-1][0]): #Above last index");
									writeLine(6,"slope=(self.data[self.numRows-1][col]-self.data[self.numRows-2][col])/(self.data[self.numRows-1][0]-self.data[self.numRows-2][0])");
									writeLine(6,"val=self.data[self.numRows-1][col]+(index-self.data[self.numRows-1][0])*slope");
								writeLine(5,"else: #Between");
									writeLine(6,"row=0");
									writeLine(6,"while(self.data[row][0]<index):row+=1");
									writeLine(6,"slope=(self.data[row][col]-self.data[row-1][col])/(self.data[row][0]-self.data[row-1][0])");
									writeLine(6,"val=self.data[row-1][col]+(index-self.data[row-1][0])*slope");
							writeLine(4,"elif(self.interpolate==\"Cubic Splines\"):");
								writeLine(5,"val=self.splines[col-1].evaluate(index)");
							writeLine(4,"");
							writeLine(4,"#Check extrapolation conditions");
							writeLine(4,"if(self.extrapolate==\"No\"):");
								writeLine(5,"if(index<=self.data[0][0]): val=self.data[0][col] #Below or at first index");
								writeLine(5,"elif(index>self.data[self.numRows-1][0]): val=self.data[self.numRows-1][col] #Above last index");
							writeLine(4,"elif(self.extrapolate==\"Left only\"): #truncate right");
								writeLine(5,"if(index>self.data[self.numRows-1][0]): val=self.data[self.numRows-1][col] #Above last index");
							writeLine(4,"elif(self.extrapolate==\"Right only\"): #truncate left");
								writeLine(5,"if(index<=self.data[0][0]): val=self.data[0][col] #Below or at first index");
						writeLine(3,"return(val)");
			writeLine(0,"");
				writeLine(1,"def calcEV(self,col):");
					writeLine(2,"ev=0");
					writeLine(2,"for r in range(0,self.numRows):");
						writeLine(3,"ev+=self.data[r][0]*self.data[r][col]");
					writeLine(2,"return(ev)");
			writeLine(0,"");
			out.close();

			out=origOut; //point back
		}
	}

	public void writeMarkovTrace(){
		try{
			FileWriter fstream = new FileWriter(dir+"MarkovTrace.py"); //Create new file
			BufferedWriter outTrace = new BufferedWriter(fstream);
			BufferedWriter temp=out;
			out=outTrace;
			writeProperties();
			
			String dimNames[]=myModel.dimInfo.dimNames;
			int numDimensions=dimNames.length;
			writeLine(0,"import csv");
			writeLine(0,"import numpy as np");
			writeLine(0,"");
			writeLine(0,"#Define MarkovTrace class");
			writeLine(0,"class MarkovTrace:");
				writeLine(1,"\"MarkovTrace class\"");
				writeLine(1,"name=\"\"");
				writeLine(1,"numStates=0");
				writeLine(1,"numCols=0");
				writeLine(1,"");
				writeLine(1,"def __init__(self,name,stateNames):");
					writeLine(2,"self.name=name");
					writeLine(2,"self.numStates=len(stateNames)");
					writeLine(2,"self.headers=[]");
					writeLine(2,"self.headers.append(\"Cycle\")");
					writeLine(2,"for s in range(0,self.numStates):");
						writeLine(3,"self.headers.append(stateNames[s])");
					for(int d=0; d<numDimensions; d++){
					writeLine(2,"self.headers.append(\"Cycle_"+dimNames[d]+"\")");
					writeLine(2,"self.headers.append(\"Cum_"+dimNames[d]+"\")");
					writeLine(2,"self.headers.append(\"Cycle_Dis_"+dimNames[d]+"\")");
					writeLine(2,"self.headers.append(\"Cum_Dis_"+dimNames[d]+"\")");
					}
					writeLine(2,"self.numCols=len(self.headers)");
					writeLine(2,"self.data=[]");
				writeLine(1,"");
				out.write("    def update(self,prev");
				for(int d=0; d<numDimensions; d++){out.write(", "+dimNames[d]+", "+dimNames[d]+"_dis");}
				out.write("):"); out.newLine();
					writeLine(2,"row=np.zeros(self.numCols)");
					writeLine(2,"cycle=len(self.data)");
					//writeLine(2,"prevRow=np.zeros(self.numCols)");
					writeLine(2,"if(cycle>0): prevRow=self.data[cycle-1]");
					writeLine(2,"row[0]=cycle");
					writeLine(2,"col=1");
					writeLine(2,"for s in range(0,len(prev)):");
						writeLine(3,"row[col]=prev[s]");
						writeLine(3,"col+=1");
					for(int d=0; d<numDimensions; d++){
					writeLine(2,"#cycle reward");
					writeLine(2,"row[col]="+dimNames[d]);
					writeLine(2,"col+=1");
					writeLine(2,"#cum reward");
					writeLine(2,"row[col]="+dimNames[d]);
					writeLine(2,"if(cycle>0): row[col]+=prevRow[col]");
					writeLine(2,"col+=1");
					writeLine(2,"#discounted cycle reward");
					writeLine(2,"row[col]="+dimNames[d]+"_dis");
					writeLine(2,"col+=1");
					writeLine(2,"#discounted cum reward");
					writeLine(2,"row[col]="+dimNames[d]+"_dis");
					writeLine(2,"if(cycle>0): row[col]+=prevRow[col]");
					writeLine(2,"col+=1");
					}
					writeLine(2,"self.data.append(row)");
				writeLine(1,"");
				writeLine(1,"def applyHalfCycle(self):");
					writeLine(2,"row=self.data[-1] #last row");
					writeLine(2,"col=self.numStates+1");
					for(int d=0; d<numDimensions; d++){
						writeLine(2,"half=row[col]*0.5");
						writeLine(2,"#cycle reward");
						writeLine(2,"row[col]=half");
						writeLine(2,"col+=1");
						writeLine(2,"#cum reward");
						writeLine(2,"row[col]-=half");
						writeLine(2,"col+=1");
						writeLine(2,"half=row[col]*0.5");
						writeLine(2,"#discounted cycle reward");
						writeLine(2,"row[col]=half");
						writeLine(2,"col+=1");
						writeLine(2,"#discounted cum reward");
						writeLine(2,"row[col]-=half");
						writeLine(2,"col+=2");
					}
				writeLine(1,"");
				writeLine(1,"def getValue(self,t,col):");
					writeLine(2,"if(isinstance(col,int)): #integer");
						writeLine(3,"val=self.data[t][col]");
						writeLine(3,"return(val)");
					writeLine(2,"else: #string");
						writeLine(3,"val=self.data[t][self.headers.index(col)]");
						writeLine(3,"return(val)");
				writeLine(1,"");
				writeLine(1,"def writeCSV(self):");
					writeLine(2,"dir=\""+dir.replaceAll("\\\\", "\\\\\\\\")+"\"");
					writeLine(2,"with open(dir+self.name+'_Trace.csv', mode='w', newline='') as trace_file:");
						writeLine(3,"trace_writer=csv.writer(trace_file)");
						writeLine(3,"trace_writer.writerow(self.headers) #headers");
						writeLine(3,"for i in range(0,len(self.data)):");
							writeLine(4,"trace_writer.writerow(self.data[i])");
				writeLine(1,"");
			writeLine(0,""); //end MarkovTrace
			
			outTrace.close();
			out=temp; //point back
			
		}catch(Exception e){
			recordError(e);
		}
	}

	
	public void writeTables(int format){
		try{
			int numTables=myModel.tables.size();
			if(numTables>0){
				writeLine(0,"#Define tables");
				if(format==0){ //In-line
					for(int i=0; i<myModel.tables.size(); i++){
						Table curTable=myModel.tables.get(i);
						if(!curTable.notes.isEmpty()){out.write("\"\"\""+curTable.notes+"\"\"\""); out.newLine();}
						out.write("headers"+curTable.name+"=[");
						for(int c=0; c<curTable.numCols-1; c++){out.write("\""+curTable.headers[c]+"\",");}
						out.write("\""+curTable.headers[curTable.numCols-1]+"\"]"); out.newLine();
						writeLine(0,"data"+curTable.name+"=[]");
						for(int r=0; r<curTable.numRows; r++){
							out.write("data"+curTable.name+".append([");
							for(int c=0; c<curTable.numCols-1; c++){out.write(curTable.data[r][c]+",");}
							writeLine(0,curTable.data[r][curTable.numCols-1]+"])");
						}
						//define table object
						out.write(curTable.name+"=Table(\""+curTable.name+"\","); //Name
						out.write("\""+curTable.type+"\",");
						out.write("\""+curTable.lookupMethod+"\",");
						out.write("\""+curTable.interpolate+"\",");
						out.write("\""+curTable.boundary+"\",");
						out.write("\""+curTable.extrapolate+"\",");
						out.write("headers"+curTable.name+", data"+curTable.name+")"); out.newLine(); //Headers + data
						if(curTable.interpolate!=null && curTable.interpolate.matches("Cubic Splines")){
							writeTableSplines(curTable);
						}
						out.newLine();
					}
				}
				else if(format==1){ //CSV
					writeLine(0,"folder=\""+dir.replaceAll("\\\\", "\\\\\\\\")+"\\\\\";");
					for(int i=0; i<myModel.tables.size(); i++){
						Table curTable=myModel.tables.get(i);
						//Write out table
						String curPath=dir+File.separator+curTable.name+".csv";
						curTable.writeCSV(curPath, myModel.errorLog);
						//define table object
						out.write(curTable.name+"=Table(\""+curTable.name+"\","); //Name
						out.write("\""+curTable.type+"\",");
						out.write("\""+curTable.lookupMethod+"\",");
						out.write("\""+curTable.interpolate+"\",");
						out.write("\""+curTable.boundary+"\",");
						out.write("\""+curTable.extrapolate+"\",");
						out.write(curTable.numRows+", "+curTable.numCols+", folder+\""+curTable.name+".csv\");"); out.newLine();
						if(curTable.interpolate!=null && curTable.interpolate.matches("Cubic Splines")){
							writeTableSplines(curTable);
						}
						writeLine(0,"");
					}
				}
			}
		}catch(Exception e){
			recordError(e);
		}
	}

	private void writeLine(int indent,String line){
		try{
			for(int i=0; i<indent; i++){out.write("    ");} //4-space indent
			out.write(line); 
			out.newLine();
		}catch(Exception e){
			recordError(e);
		}
	}
	
	private void recordError(Exception e){
		e.printStackTrace();
		myModel.errorLog.recordError(e);
		JOptionPane.showMessageDialog(null, e.toString());
	}
}
